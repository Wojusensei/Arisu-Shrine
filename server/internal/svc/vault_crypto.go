package svc

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"io"
)

// 网页端账号档案的令牌加密。
//
// 背景：安卓的 AccountVault 把 refresh 令牌存在本机 DataStore（设备上），
// 网页端要「免密切换账号」，等价物只能落在服务端。但 refresh 令牌是明文即
// 可用的凭据：直接入库等于拖库即可接管账号。
//
// 折中：用 AES-GCM 加密后入库，密钥由 jwt.secret 派生出独立子钥
// （不同用途加不同 label，密钥轮换互不影响）。拖库者没有 jwt.secret
// 就解不开密文，与「只存哈希」的强度差距收敛到「密钥是否泄露」这一层。
//
// 附带完整性：GCM 自带认证标签，密文被篡改会直接解不开，按失效处理。

const vaultKeyLabel = "arisu-web-vault-v1"

func vaultKey(secret string) [32]byte {
	// 派生而不是直接用 jwt.secret：两处用途不共享密钥材料
	h := sha256.Sum256([]byte(vaultKeyLabel + "|" + secret))
	return h
}

// SealToken 加密一枚令牌，返回 base64(nonce||ciphertext)
func SealToken(secret, token string) (string, error) {
	if token == "" {
		return "", nil
	}
	key := vaultKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	out := gcm.Seal(nonce, nonce, []byte(token), nil)
	return base64.RawURLEncoding.EncodeToString(out), nil
}

// OpenToken 解密；解不开（密钥变了/密文损坏）返回空串，调用方按「需要重新登录」处理
func OpenToken(secret, sealed string) string {
	if sealed == "" {
		return ""
	}
	raw, err := base64.RawURLEncoding.DecodeString(sealed)
	if err != nil {
		return ""
	}
	key := vaultKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return ""
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return ""
	}
	if len(raw) < gcm.NonceSize() {
		return ""
	}
	nonce, ct := raw[:gcm.NonceSize()], raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, ct, nil)
	if err != nil {
		return ""
	}
	return string(plain)
}

// ErrNoVaultKey 保留给调用方区分「没配置密钥」与「解密失败」
var ErrNoVaultKey = errors.New("vault key unavailable")
