package model

import "fmt"

// 角色与权限等级（数值越大权限越高）
const (
	RoleMember     = "member"      // 社员
	RoleTempHoushi = "temp_houshi" // 临时奉仕
	RoleHoushi     = "houshi"      // 奉仕
	RoleGuuji      = "guuji"       // 宫司
	RoleKannushi   = "kannushi"    // 神主
)

var roleRank = map[string]int{
	RoleMember: 0, RoleTempHoushi: 1, RoleHoushi: 2, RoleGuuji: 3, RoleKannushi: 4,
}

var roleNames = map[string]string{
	RoleMember: "社员", RoleTempHoushi: "临时奉仕", RoleHoushi: "奉仕",
	RoleGuuji: "宫司", RoleKannushi: "神主",
}

func RoleRank(role string) int    { return roleRank[role] }
func RoleName(role string) string { return roleNames[role] }
func IsAdmin(role string) bool    { return roleRank[role] >= roleRank[RoleHoushi] } // 奉仕及以上（可删帖/处理举报/警告）
func IsGuuji(role string) bool    { return roleRank[role] >= roleRank[RoleGuuji] }  // 宫司及以上（置顶精华/禁言/封禁/发日志）
func IsKannushi(role string) bool { return role == RoleKannushi }

func FormatUserNo(id int64) string { return fmt.Sprintf("User_%06d", id) }
func FormatPostNo(id int64) string { return fmt.Sprintf("arisu_%06d", id) }

// FormatPostNo2 通知路由用（与客户端路由 post/{postId} 对齐）。
// 历史教训：这里曾返回 arisu_%06d 帖号，客户端按 post/ 前缀分发，
// 社务箱所有回复/追踪通知点了都没反应。
func FormatPostNo2(id int64) string { return fmt.Sprintf("post/%d", id) }

// ---- 用户 ----

type UserBrief struct {
	ID        int64  `json:"id"`
	No        string `json:"no"`
	Nickname  string `json:"nickname"`
	Role      string `json:"role"`
	Avatar    string `json:"avatar"`    // 展示用头像 URL（活跃头像开且有时用活跃，否则固定）
	Following bool   `json:"following"` // 查看者是否已关注该社员（列表场景）
}

type User struct {
	UserBrief
	// 邮箱仅在社员自行公开时下发
	Email          string `json:"email,omitempty"`
	Signature      string `json:"signature"`
	StatusEmoji    string `json:"status_emoji"`
	StatusText     string `json:"status_text"`
	BlogURL        string `json:"blog_url"`
	CustomMarkdown string `json:"custom_markdown"`
	CustomCSS      string `json:"custom_css"`
	JoinedAt       int64  `json:"joined_at"`
	OnlineMinutes  int64  `json:"online_minutes"`
	FollowingCount int64  `json:"following_count"`
	FollowerCount  int64  `json:"follower_count"`
	PostCount      int64  `json:"post_count"`
	// 账面（单位 0.1 円）：每日上线 +50 円、初始 100 円，奉纳支出、被奉纳收 10%（零头不丢）
	BalanceTenths int64 `json:"balance_tenths"`
}

// Me 自有视角（多出仅本人可见字段）
type Me struct {
	User
	Email               string          `json:"email"`
	FixedAvatarURL      string          `json:"fixed_avatar_url"`
	ActiveAvatarURL     string          `json:"active_avatar_url"`
	ActiveAvatarEnabled bool            `json:"active_avatar_enabled"`
	ShowFields          map[string]bool `json:"show_fields"`
	NotifyScope         map[string]bool `json:"notify_scope"`
	MutedUntil          *int64          `json:"muted_until"` // nil=未禁言
	DeactivatedAt       *int64          `json:"deactivated_at"`
}

// ---- 帖子 ----

type Post struct {
	ID           int64     `json:"id"`
	No           string    `json:"no"`
	Board        string    `json:"board"` // main|gensokyo|dev
	Title        string    `json:"title"`
	Body         string    `json:"body"`
	Intro        string    `json:"intro"`
	CoverURL     string    `json:"cover_url"`
	Pinned       bool      `json:"pinned"`
	Essence      bool      `json:"essence"`
	Author       UserBrief `json:"author"`
	UpCount      int64     `json:"up_count"`
	CommentCount int64     `json:"comment_count"`
	SaveCount    int64     `json:"save_count"`
	OfferTotal   int64     `json:"offer_total"`
	Offered      bool      `json:"offered"`
	Upped        bool      `json:"upped"`
	Tracked      bool      `json:"tracked"`
	Favorited    bool      `json:"favorited"`
	CreatedAt    int64     `json:"created_at"`
	UpdatedAt    int64     `json:"updated_at"`
	Edited       bool      `json:"edited"`
	Deleted      bool      `json:"deleted"`
	// 正文里 @User_编号 提到的社员（读时解析），客户端渲染时用昵称顶替编号展示
	Mentions []UserBrief `json:"mentions,omitempty"`
}

type Comment struct {
	ID          int64      `json:"id"`
	PostID      int64      `json:"post_id"`
	ParentID    *int64     `json:"parent_id"`
	ReplyToUser *UserBrief `json:"reply_to_user"`
	Author      UserBrief  `json:"author"`
	Content     string     `json:"content"`
	// 评论附图（可空串）：本站图床的相对/绝对 URL，见 CommentImageURL 校验
	ImageURL  string `json:"image_url"`
	LikeCount int64  `json:"like_count"`
	Liked     bool   `json:"liked"`
	Mine      bool   `json:"mine"`
	CreatedAt int64  `json:"created_at"`
	// 评论内容里 @User_编号 提到的社员（读时解析），渲染时顶替编号展示
	Mentions []UserBrief `json:"mentions,omitempty"`
}

// ---- 绘马 ----

type Ema struct {
	ID         int64     `json:"id"`
	Title      string    `json:"title"`
	Body       string    `json:"body"`
	Tags       []string  `json:"tags"`
	State      string    `json:"state"`
	Author     UserBrief `json:"author"`
	ReplyCount int64     `json:"reply_count"`
	CreatedAt  int64     `json:"created_at"`
	UpdatedAt  int64     `json:"updated_at"`
}

type EmaReply struct {
	ID        int64     `json:"id"`
	EmaID     int64     `json:"ema_id"`
	Author    UserBrief `json:"author"`
	Content   string    `json:"content"`
	CreatedAt int64     `json:"created_at"`
}

// ---- 收藏 ----

type Collection struct {
	ID        int64  `json:"id"`
	Name      string `json:"name"`
	IsDefault bool   `json:"is_default"`
	Count     int64  `json:"count"`
	CoverURL  string `json:"cover_url"`
}

// ---- 表情包 ----

type Sticker struct {
	ID        int64  `json:"id"`
	URL       string `json:"url"`
	Animated  bool   `json:"animated"`
	CreatedAt int64  `json:"created_at"`
}

// ---- 通知（社务箱） ----

type Notice struct {
	ID          int64  `json:"id"`
	Kind        string `json:"kind"`
	Category    string `json:"category"` // reply|system|report
	Content     string `json:"content"`
	TargetRoute string `json:"target_route"` // post/13、ema/3、user/5、staff、changelog
	Read        bool   `json:"read"`
	Starred     bool   `json:"starred"`
	CreatedAt   int64  `json:"created_at"`
}

// ---- 版本日志 ----

type Release struct {
	ID         int64  `json:"id"`
	Version    string `json:"version"`
	Channel    string `json:"channel"`     // stable|beta
	ChangeType string `json:"change_type"` // feature|fix|improvement|breaking
	Body       string `json:"body"`
	CreatedAt  int64  `json:"created_at"`
}

// ---- 游会 ----

type Omikuji struct {
	Luck      int    `json:"luck"`
	Result    string `json:"result"` // daikichi|kichi|chuukichi|shoukichi|suekichi|kyou
	Character string `json:"character"`
	Series    string `json:"series"`
	Fortune   string `json:"fortune"`
	LuckyItem string `json:"lucky_item"`
	DrawnAt   int64  `json:"drawn_at"`
}

type OnlineStats struct {
	OnlineNow    int64 `json:"online_now"`
	Registered   int64 `json:"registered"`
	TodayPeak    int64 `json:"today_peak"`
	TodayMinutes int64 `json:"today_minutes"`
	TotalMinutes int64 `json:"total_minutes"`
}

// ---- 参拜热度（仿 GitHub 绿瓷砖） ----

type Tile struct {
	Date     string `json:"date"`  // 2006-01-02
	Score    int    `json:"score"` // 0~4 级（0 空）
	Posts    int    `json:"posts"`
	Comments int    `json:"comments"`
	Minutes  int    `json:"minutes"`
	Omikuji  int    `json:"omikuji"` // 当日是否求过签（0/1）
}

// ---- 奉仕会 ----

type StaffGroup struct {
	Role     string        `json:"role"`
	RoleName string        `json:"role_name"`
	Members  []StaffMember `json:"members"`
}

type StaffMember struct {
	UserBrief
	Signature string `json:"signature"`
	JoinedAt  int64  `json:"joined_at"`
}

type StaffApplication struct {
	ID          int64     `json:"id"`
	User        UserBrief `json:"user"`
	Reason      string    `json:"reason"`
	WeeklyHours string    `json:"weekly_hours"`
	Status      string    `json:"status"`
	CreatedAt   int64     `json:"created_at"`
}

// ---- 举报 ----

type Report struct {
	ID             int64     `json:"id"`
	Reporter       UserBrief `json:"reporter"`
	PostID         *int64    `json:"post_id"`
	CommentID      *int64    `json:"comment_id"`
	EmaID          *int64    `json:"ema_id"`
	ReportedUserID *int64    `json:"reported_user_id"`
	Reason         string    `json:"reason"`
	Status         string    `json:"status"`
	CreatedAt      int64     `json:"created_at"`
}

// ---- 成就 ----

type Achievement struct {
	Code       string `json:"code"`
	UnlockedAt int64  `json:"unlocked_at"`
}

// ---- 认证 ----

type AuthResult struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	Me           *Me    `json:"me"`
}

// ---- 分页 ----

type Page struct {
	Page     int   `json:"page"`
	PageSize int   `json:"page_size"`
	Total    int64 `json:"total"`
}

type PostPage struct {
	Page
	Items []Post `json:"items"`
}

type EmaPage struct {
	Page
	Items []Ema `json:"items"`
}

type NoticePage struct {
	Page
	Items  []Notice `json:"items"`
	Unread int64    `json:"unread"`
}

type SearchResult struct {
	Posts []Post      `json:"posts"`
	Users []UserBrief `json:"users"`
}
