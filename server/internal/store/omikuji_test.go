package store

import (
	"math"
	"testing"
)

func TestDrawLuckDistribution(t *testing.T) {
	const n = 200000
	want := [6]float64{0.8, 6.2, 19.5, 33.1, 30.7, 9.7}
	names := [6]string{"凶", "末吉", "小吉", "中吉", "吉", "大吉"}
	counts := [6]int{}
	for i := 0; i < n; i++ {
		v := drawLuck()
		if v < 1 || v > 100 {
			t.Fatalf("幸运值越界：%d", v)
		}
		switch {
		case v <= 10:
			counts[0]++
		case v <= 30:
			counts[1]++
		case v <= 50:
			counts[2]++
		case v <= 70:
			counts[3]++
		case v <= 90:
			counts[4]++
		default:
			counts[5]++
		}
	}
	for i, w := range want {
		got := float64(counts[i]) * 100 / n
		t.Logf("%s：%.2f%%（期望 %.1f%%）", names[i], got, w)
		if math.Abs(got-w) > 1.5 {
			t.Errorf("%s 占比 %.2f%%，偏离期望 %.1f%% 超过 1.5 个百分点", names[i], got, w)
		}
	}
}
