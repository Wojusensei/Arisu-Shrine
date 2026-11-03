package store

import "testing"

func TestTileLevelCountsOmikuji(t *testing.T) {
	cases := []struct {
		name                          string
		minutes, posts, comments, omi int
		want                          int
	}{
		{"空白", 0, 0, 0, 0, 0},
		{"只求签", 0, 0, 0, 1, 1},
		{"求签加一刻钟", 15, 0, 0, 1, 1},
		{"求签加一条评论", 0, 0, 1, 1, 1},
		{"发一帖", 0, 1, 0, 0, 2},
		{"求签再加一帖", 0, 1, 0, 1, 2},
		{"发两帖", 0, 2, 0, 0, 3},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := tileLevel(c.minutes, c.posts, c.comments, c.omi); got != c.want {
				t.Fatalf("tileLevel(%d,%d,%d,%d) = %d, want %d",
					c.minutes, c.posts, c.comments, c.omi, got, c.want)
			}
		})
	}
}
