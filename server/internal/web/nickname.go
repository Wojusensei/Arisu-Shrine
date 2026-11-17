package web

import "strings"

// 身份名与系统占位名不许社员占用：昵称会和身份标签并排显示，叫「神主」会被误认成管理；
// 「已注销的社员」是匿名化账号的占位名（匿名行不在昵称唯一索引里，本来可以重复，活人不该抢）。
var reservedNicknames = map[string]bool{
	"神主":     true,
	"宫司":     true,
	"奉仕":     true,
	"临时奉仕":   true,
	"社员":     true,
	"已注销的社员": true,
	"有栖神社":   true,
}

// nicknameReserved 保留名判定：按去空白后的原样比较（中文无大小写）
func nicknameReserved(nick string) bool {
	return reservedNicknames[strings.TrimSpace(nick)]
}
