package web

import "strings"

var reservedNicknames = map[string]bool{
	"神主":     true,
	"宫司":     true,
	"奉仕":     true,
	"临时奉仕":   true,
	"社员":     true,
	"已注销的社员": true,
	"有栖神社":   true,
}

func nicknameReserved(nick string) bool {
	return reservedNicknames[strings.TrimSpace(nick)]
}
