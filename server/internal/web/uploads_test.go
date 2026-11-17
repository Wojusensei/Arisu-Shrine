package web

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"os"
	"path/filepath"
	"testing"
)

func TestThumbWidthOfWhitelist(t *testing.T) {
	cases := map[string]int{
		"400": 400, "800": 800, "200": 0, "1": 0, "abc": 0, "": 0, "-400": 0, "400.0": 0,
	}
	for in, want := range cases {
		if got := thumbWidthOf(in); got != want {
			t.Errorf("thumbWidthOf(%q) = %d, want %d（白名单外一律回退原图）", in, got, want)
		}
	}
}

// 面积平均：2×2 四个像素缩成 1×1 时取其均值
func TestScaleDownAreaAverages(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 2, 2))
	src.SetRGBA(0, 0, color.RGBA{R: 0, G: 0, B: 0, A: 255})
	src.SetRGBA(1, 0, color.RGBA{R: 255, G: 0, B: 0, A: 255})
	src.SetRGBA(0, 1, color.RGBA{R: 0, G: 255, B: 0, A: 255})
	src.SetRGBA(1, 1, color.RGBA{R: 0, G: 0, B: 255, A: 255})
	out := scaleDownArea(src, 1)
	if out.Bounds().Dx() != 1 || out.Bounds().Dy() != 1 {
		t.Fatalf("缩放后尺寸 = %dx%d, want 1x1", out.Bounds().Dx(), out.Bounds().Dy())
	}
	r, g, b, _ := out.At(0, 0).RGBA()
	if r>>8 != 63 || g>>8 != 63 || b>>8 != 63 {
		t.Errorf("均值 = (%d,%d,%d), want (63,63,63)", r>>8, g>>8, b>>8)
	}
}

// 长边按 target 收，短边等比，不超界
func TestScaleDownAreaKeepsRatio(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 1200, 600))
	out := scaleDownArea(src, 400)
	if out.Bounds().Dx() != 400 || out.Bounds().Dy() != 200 {
		t.Errorf("缩放后 = %dx%d, want 400x200", out.Bounds().Dx(), out.Bounds().Dy())
	}
}

// 端到端：JPEG 进 → 缩小后的 JPEG 出，且可正常解码
func TestBuildThumbJPEG(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "big.jpg")
	var buf bytes.Buffer
	img := image.NewRGBA(image.Rect(0, 0, 1000, 500))
	for y := 0; y < 500; y++ {
		for x := 0; x < 1000; x++ {
			img.SetRGBA(x, y, color.RGBA{R: uint8(x / 4), G: uint8(y / 2), B: 128, A: 255})
		}
	}
	if err := jpeg.Encode(&buf, img, nil); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(src, buf.Bytes(), 0o644); err != nil {
		t.Fatal(err)
	}
	out, err := buildThumb(src, 400, ".jpg")
	if err != nil {
		t.Fatal(err)
	}
	cfg, format, err := image.DecodeConfig(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("缩略图解不开：%v", err)
	}
	if format != "jpeg" || cfg.Width != 400 || cfg.Height != 200 {
		t.Errorf("缩略图 = %s %dx%d, want jpeg 400x200", format, cfg.Width, cfg.Height)
	}
	if len(out) >= buf.Len() {
		t.Errorf("缩略图 %d 字节不小于原图 %d 字节", len(out), buf.Len())
	}
}

// PNG 源出 PNG：透明像素保留 alpha，不会被压成黑底
func TestBuildThumbPNGKeepsAlpha(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "avatar.png")
	var buf bytes.Buffer
	img := image.NewRGBA(image.Rect(0, 0, 600, 600))
	for y := 0; y < 600; y++ {
		for x := 0; x < 600; x++ {
			a := uint8(0)
			if x > 300 {
				a = 255
			}
			img.SetRGBA(x, y, color.RGBA{R: 200, G: 30, B: 30, A: a})
		}
	}
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(src, buf.Bytes(), 0o644); err != nil {
		t.Fatal(err)
	}
	out, err := buildThumb(src, 400, ".png")
	if err != nil {
		t.Fatal(err)
	}
	cfg, format, err := image.DecodeConfig(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("缩略图解不开：%v", err)
	}
	if format != "png" || cfg.Width != 400 {
		t.Errorf("缩略图 = %s %d宽, want png 400宽", format, cfg.Width)
	}
	decoded, err := png.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatal(err)
	}
	if _, _, _, a := decoded.At(50, 300).RGBA(); a>>8 > 8 {
		t.Errorf("左侧本应透明，实测 alpha=%d", a>>8)
	}
	if _, _, _, a := decoded.At(350, 300).RGBA(); a>>8 < 247 {
		t.Errorf("右侧本应不透明，实测 alpha=%d", a>>8)
	}
}

// gif/webp 等不做缩略（动图不能被拍成静帧），由调用方原样下发
func TestThumbSkipsAnimatedFormats(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{"a.gif", "b.webp", "c.apk"} {
		p := filepath.Join(dir, name)
		if err := os.WriteFile(p, []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
		ext := filepath.Ext(p)
		if ext == ".jpg" || ext == ".png" {
			t.Fatalf("%s 不该进入缩略分支", name)
		}
	}
}

// 像素数上限：小体积、大尺寸的图必须被拦下（体积闸只看字节数拦不住它）
func TestThumbTooLargeByPixelCount(t *testing.T) {
	cases := []struct {
		w, h int
		want bool
	}{
		{2048, 2048, false},  // App 上传的长边上限（≈4MP）
		{8000, 5000, false},  // 40.0MP，在 40Mi 像素以内
		{8192, 5120, false},  // 正好等于上限
		{8193, 5120, true},   // 过线一格
		{20000, 20000, true}, // 400MP：落盘不到 0.5MB，解码要 380MB、1.7 秒
		{60000, 60000, true}, // 3.4GB 级
		{0, 100, true},       // 尺寸读不出来
		{-1, -1, true},       //
	}
	for _, c := range cases {
		if got := thumbTooLarge(image.Config{Width: c.w, Height: c.h}); got != c.want {
			t.Errorf("thumbTooLarge(%dx%d) = %v, want %v", c.w, c.h, got, c.want)
		}
	}
}
