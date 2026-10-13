package model

import "fmt"

const (
	RoleMember     = "member"
	RoleTempHoushi = "temp_houshi"
	RoleHoushi     = "houshi"
	RoleGuuji      = "guuji"
	RoleKannushi   = "kannushi"
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
func IsAdmin(role string) bool    { return roleRank[role] >= roleRank[RoleHoushi] }
func IsGuuji(role string) bool    { return roleRank[role] >= roleRank[RoleGuuji] }
func IsKannushi(role string) bool { return role == RoleKannushi }

func FormatUserNo(id int64) string { return fmt.Sprintf("User_%06d", id) }
func FormatPostNo(id int64) string { return fmt.Sprintf("arisu_%06d", id) }

func FormatPostNo2(id int64) string { return fmt.Sprintf("post/%d", id) }

type UserBrief struct {
	ID        int64  `json:"id"`
	No        string `json:"no"`
	Nickname  string `json:"nickname"`
	Role      string `json:"role"`
	Avatar    string `json:"avatar"`
	Following bool   `json:"following"`
}

type User struct {
	UserBrief

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
}

type Me struct {
	User
	Email               string          `json:"email"`
	FixedAvatarURL      string          `json:"fixed_avatar_url"`
	ActiveAvatarURL     string          `json:"active_avatar_url"`
	ActiveAvatarEnabled bool            `json:"active_avatar_enabled"`
	ShowFields          map[string]bool `json:"show_fields"`
	NotifyScope         map[string]bool `json:"notify_scope"`
	MutedUntil          *int64          `json:"muted_until"`
	DeactivatedAt       *int64          `json:"deactivated_at"`
}

type Post struct {
	ID           int64     `json:"id"`
	No           string    `json:"no"`
	Board        string    `json:"board"`
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
	Upped        bool      `json:"upped"`
	Tracked      bool      `json:"tracked"`
	Favorited    bool      `json:"favorited"`
	CreatedAt    int64     `json:"created_at"`
	UpdatedAt    int64     `json:"updated_at"`
	Edited       bool      `json:"edited"`
	Deleted      bool      `json:"deleted"`
}

type Comment struct {
	ID          int64      `json:"id"`
	PostID      int64      `json:"post_id"`
	ParentID    *int64     `json:"parent_id"`
	ReplyToUser *UserBrief `json:"reply_to_user"`
	Author      UserBrief  `json:"author"`
	Content     string     `json:"content"`

	ImageURL  string `json:"image_url"`
	LikeCount int64  `json:"like_count"`
	Liked     bool   `json:"liked"`
	Mine      bool   `json:"mine"`
	CreatedAt int64  `json:"created_at"`
}

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

type Collection struct {
	ID        int64  `json:"id"`
	Name      string `json:"name"`
	IsDefault bool   `json:"is_default"`
	Count     int64  `json:"count"`
	CoverURL  string `json:"cover_url"`
}

type Sticker struct {
	ID        int64  `json:"id"`
	URL       string `json:"url"`
	Animated  bool   `json:"animated"`
	CreatedAt int64  `json:"created_at"`
}

type Notice struct {
	ID          int64  `json:"id"`
	Kind        string `json:"kind"`
	Category    string `json:"category"`
	Content     string `json:"content"`
	TargetRoute string `json:"target_route"`
	Read        bool   `json:"read"`
	Starred     bool   `json:"starred"`
	CreatedAt   int64  `json:"created_at"`
}

type Release struct {
	ID         int64  `json:"id"`
	Version    string `json:"version"`
	Channel    string `json:"channel"`
	ChangeType string `json:"change_type"`
	Body       string `json:"body"`
	CreatedAt  int64  `json:"created_at"`
}

type Omikuji struct {
	Luck      int    `json:"luck"`
	Result    string `json:"result"`
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

type Tile struct {
	Date     string `json:"date"`
	Score    int    `json:"score"`
	Posts    int    `json:"posts"`
	Comments int    `json:"comments"`
	Minutes  int    `json:"minutes"`
}

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

type Achievement struct {
	Code       string `json:"code"`
	UnlockedAt int64  `json:"unlocked_at"`
}

type AuthResult struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	Me           *Me    `json:"me"`
}

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
