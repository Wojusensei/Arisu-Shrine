package web

import "testing"

// 保留昵称：身份名与系统占位名不许社员占用
func TestNicknameReserved(t *testing.T) {
	for _, n := range []string{"神主", " 宫司 ", "奉仕", "临时奉仕", "社员", "已注销的社员", "有栖神社"} {
		if !nicknameReserved(n) {
			t.Errorf("nicknameReserved(%q) = false, want true（保留名）", n)
		}
	}
	for _, n := range []string{"Ether♪", "莫耶尔", "神主的小号", "社员001", "", "  "} {
		if nicknameReserved(n) {
			t.Errorf("nicknameReserved(%q) = true, want false（普通昵称）", n)
		}
	}
}
