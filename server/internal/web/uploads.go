package web

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"strings"
	"sync/atomic"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// 列表/封面用的缩略图：?w=400 / ?w=800（长边像素，白名单——不设白名单等于
// 让人拿任意尺寸把磁盘刷爆）。
//
// 只对 jpg/png 出手：gif 是动图、webp stdlib 解不了，硬缩会把动图拍成静帧，
// 宁可原样下发。PNG 源出 PNG（保透明：头像是圆的、表情包带 alpha，
// 压成 JPEG 会糊出黑边）；JPEG 源出 JPEG。
var thumbWidths = map[int]bool{400: true, 800: true}

// thumbMaxSource 源图超过这个体积不做缩略：解一张超大图要几百毫秒和几十 MB 堆，
// 而 App 的 ?w= 请求一屏十几个，付不起。直接给原图。
const thumbMaxSource = 8 << 20

// thumbMaxPixels 源图像素数上限。只看字节数拦不住「小体积大尺寸」：
// 一张 20000×20000 的纯色 PNG 落盘才 400 多 KB（远低于上面的体积闸），
// 解码却要 380MB 堆、1.7 秒 CPU——按像素线性外推，60000×60000 的包
// 仍是 8MB 以内，解码一次就要 3GB 以上，够把 1C1G/2C2G 的机器连库一起打死
// （进程无内存上限，OOM 杀谁不由我们决定）。App 上传前统一压到长边 ≤2048
// （≈4MP），40MP 留了十倍余量，正常图片撞不到。
const thumbMaxPixels = 40 << 20

// thumbWidthOf 解析 ?w= 参数，非白名单值返回 0（照常发原图）
func thumbWidthOf(q string) int {
	n, err := strconv.Atoi(q)
	if err != nil || !thumbWidths[n] {
		return 0
	}
	return n
}

// thumbTooLarge 源图是否超出可解码范围。用除法判 w*h > 上限：
// 两个 int 相乘在 32 位平台会溢出，除法没有这个问题
func thumbTooLarge(cfg image.Config) bool {
	if cfg.Width <= 0 || cfg.Height <= 0 {
		return true
	}
	return cfg.Width > thumbMaxPixels/cfg.Height
}

// thumbSlots 同时进行的缩略图解码上限。像素闸（40MP）只保证单张不炸，
// 并发放开的话，一排冷缩略图各要上百 MB 堆，照样能把内存打尖。
// 满了不发错误页：照常给原图（原图本就是公开 URL 直出，没有新增暴露面）
var thumbSlots = make(chan struct{}, 2)

// thumbTmpSeq 缩略图临时文件名的进程内序号：pid 相同的并发写入靠它错开
var thumbTmpSeq atomic.Int64

func acquireThumbSlot() bool {
	select {
	case thumbSlots <- struct{}{}:
		return true
	default:
		return false
	}
}

func releaseThumbSlot() { <-thumbSlots }

// serveThumb 尝试以缩略图响应；返回 false 表示调用方照常发原图
func (w *Web) serveThumb(c *gin.Context, width int) bool {
	rel := strings.TrimPrefix(c.Request.URL.Path, "/uploads/")
	rel = strings.TrimPrefix(path.Clean("/"+rel), "/") // 规范化后再取相对，挡掉 ../
	if rel == "" || rel == "." {
		return false
	}
	ext := strings.ToLower(filepath.Ext(rel))
	if ext != ".jpg" && ext != ".jpeg" && ext != ".png" {
		return false
	}
	root := w.Cfg.Upload.Dir
	src := filepath.Join(root, filepath.FromSlash(rel))
	fi, err := os.Stat(src)
	if err != nil || fi.IsDir() || fi.Size() > thumbMaxSource {
		return false
	}
	// 先只读文件头：原图本来就比目标还小就没什么可缩的；
	// 像素数超上限同理（decodeConfig 只读头，开销可忽略，真正的解码在后面）
	cfg, err := decodeConfig(src)
	if err != nil || maxInt(cfg.Width, cfg.Height) <= width {
		return false
	}
	if thumbTooLarge(cfg) {
		w.Log.Warn("缩略图跳过：源图像素数超限",
			zap.String("src", rel), zap.Int("w", cfg.Width), zap.Int("h", cfg.Height))
		return false
	}
	dst := filepath.Join(root, "thumbs",
		filepath.FromSlash(strings.TrimSuffix(rel, filepath.Ext(rel))+"_w"+strconv.Itoa(width)+ext))

	body, err := os.ReadFile(dst)
	if err != nil {
		if !acquireThumbSlot() {
			return false
		}
		body, err = buildThumb(src, width, ext)
		releaseThumbSlot()
		if err != nil {
			// 生成失败不是错误页的理由：回退原图，用户能看图
			w.Log.Warn("缩略图生成失败，回退原图", zap.String("src", rel), zap.Error(err))
			return false
		}
		if err := os.MkdirAll(filepath.Dir(dst), 0o755); err == nil {
			// 并发同图各写各的临时名，最后 rename 落定：不会读到半截文件。
			// 临时名必须进程内唯一：两个 goroutine 同用 pid 名时，后写的 O_TRUNC
			// 恰好落在先写者的 WriteFile 与 Rename 之间，rename 出去的会是截断文件，
			// 还会被 immutable 缓存一年
			tmp := dst + ".tmp-" + strconv.Itoa(os.Getpid()) + "-" + strconv.FormatInt(thumbTmpSeq.Add(1), 10)
			if err := os.WriteFile(tmp, body, 0o644); err == nil {
				if err := os.Rename(tmp, dst); err != nil {
					_ = os.Remove(tmp)
				}
			} else {
				_ = os.Remove(tmp)
			}
		}
	}
	c.Header("X-Content-Type-Options", "nosniff")
	if ext == ".png" {
		c.Data(http.StatusOK, "image/png", body)
	} else {
		c.Data(http.StatusOK, "image/jpeg", body)
	}
	return true
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

// decodeConfig 只读文件头取尺寸
func decodeConfig(src string) (image.Config, error) {
	f, err := os.Open(src)
	if err != nil {
		return image.Config{}, err
	}
	defer f.Close()
	cfg, _, err := image.DecodeConfig(f)
	return cfg, err
}

// buildThumb 解码 → 面积平均降采样 → 按源格式编码
func buildThumb(src string, width int, ext string) ([]byte, error) {
	f, err := os.Open(src)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	img, _, err := image.Decode(f)
	if err != nil {
		return nil, err
	}
	small := scaleDownArea(img, width)
	var buf bytes.Buffer
	if ext == ".png" {
		err = png.Encode(&buf, small)
	} else {
		err = jpeg.Encode(&buf, small, &jpeg.Options{Quality: 82})
	}
	if err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// scaleDownArea 面积平均缩小到长边不超过 target。
// 每个输出像素取源矩形内所有像素的均值（在预乘 RGBA 空间做，半透明边缘
// 不会发黑）。像素级 O(源像素数)，一张 2000×1500 的图约几十毫秒，
// 落地后不再重算——这个代价换来零第三方依赖。
func scaleDownArea(src image.Image, target int) *image.RGBA {
	b := src.Bounds()
	sw, sh := b.Dx(), b.Dy()
	long := maxInt(sw, sh)
	dw, dh := sw, sh
	if long > target {
		dw = sw * target / long
		dh = sh * target / long
	}
	if dw < 1 {
		dw = 1
	}
	if dh < 1 {
		dh = 1
	}
	dst := image.NewRGBA(image.Rect(0, 0, dw, dh))
	for y := 0; y < dh; y++ {
		y0 := b.Min.Y + y*sh/dh
		y1 := b.Min.Y + (y+1)*sh/dh
		if y1 <= y0 {
			y1 = y0 + 1
		}
		for x := 0; x < dw; x++ {
			x0 := b.Min.X + x*sw/dw
			x1 := b.Min.X + (x+1)*sw/dw
			if x1 <= x0 {
				x1 = x0 + 1
			}
			var r, g, bl, a uint64
			var n uint64
			for yy := y0; yy < y1; yy++ {
				for xx := x0; xx < x1; xx++ {
					cr, cg, cb, ca := src.At(xx, yy).RGBA()
					r += uint64(cr)
					g += uint64(cg)
					bl += uint64(cb)
					a += uint64(ca)
					n++
				}
			}
			if n == 0 {
				continue
			}
			dst.SetRGBA(x, y, color.RGBA{
				R: uint8(r / n >> 8),
				G: uint8(g / n >> 8),
				B: uint8(bl / n >> 8),
				A: uint8(a / n >> 8),
			})
		}
	}
	return dst
}
