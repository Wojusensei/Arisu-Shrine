package web

import (
	"reflect"
	"testing"
)

func TestParseMentionIDs(t *testing.T) {
	cases := []struct {
		name string
		in   string
		want []int64
	}{
		{"标准形态", "叫上 @User_000123 一起", []int64{123}},
		{"无前导零", "@User_7 来看", []int64{7}},
		{"大小写不敏感", "@user_42 @USER_42", []int64{42}},
		{"多个去重", "@User_1 和 @User_1 以及 @User_2", []int64{1, 2}},
		{"混在中文里", "求 @User_0031 看看这段 @User_88", []int64{31, 88}},
		{"非法忽略", "@User_abc @User_ @User_", nil},
		{"编号零忽略", "@User_0 @User_0000", nil},
		{"普通@不受影响", "@某某 晚上见", nil},
		{"截断不留半截", "@User_123abc", []int64{123}},
	}
	for _, c := range cases {
		if got := parseMentionIDs(c.in); !reflect.DeepEqual(got, c.want) {
			t.Errorf("%s: parseMentionIDs(%q) = %v, want %v", c.name, c.in, got, c.want)
		}
	}
}

func TestParseMentionIDsCap(t *testing.T) {
	in := ""
	for i := 1; i <= 50; i++ {
		in += "@User_" + itoa(i) + " "
	}
	got := parseMentionIDs(in)
	if len(got) != mentionCap {
		t.Fatalf("len = %d, want %d", len(got), mentionCap)
	}
	for i, id := range got {
		if id != int64(i+1) {
			t.Fatalf("got[%d] = %d, want %d", i, id, i+1)
		}
	}
}
