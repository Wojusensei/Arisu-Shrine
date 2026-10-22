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

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

var thumbWidths = map[int]bool{400: true, 800: true}

const thumbMaxSource = 8 << 20

const thumbMaxPixels = 40 << 20

func thumbWidthOf(q string) int {
	n, err := strconv.Atoi(q)
	if err != nil || !thumbWidths[n] {
		return 0
	}
	return n
}

func thumbTooLarge(cfg image.Config) bool {
	if cfg.Width <= 0 || cfg.Height <= 0 {
		return true
	}
	return cfg.Width > thumbMaxPixels/cfg.Height
}

func (w *Web) serveThumb(c *gin.Context, width int) bool {
	rel := strings.TrimPrefix(c.Request.URL.Path, "/uploads/")
	rel = strings.TrimPrefix(path.Clean("/"+rel), "/")
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
		body, err = buildThumb(src, width, ext)
		if err != nil {

			w.Log.Warn("缩略图生成失败，回退原图", zap.String("src", rel), zap.Error(err))
			return false
		}
		if err := os.MkdirAll(filepath.Dir(dst), 0o755); err == nil {

			tmp := dst + ".tmp-" + strconv.Itoa(os.Getpid())
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

func decodeConfig(src string) (image.Config, error) {
	f, err := os.Open(src)
	if err != nil {
		return image.Config{}, err
	}
	defer f.Close()
	cfg, _, err := image.DecodeConfig(f)
	return cfg, err
}

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
