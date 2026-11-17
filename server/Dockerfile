# 多阶段构建：产物为单个静态二进制 + 迁移已内嵌
FROM golang:1.24-alpine AS build
WORKDIR /src
ENV GOPROXY=https://goproxy.cn,direct
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags "-s -w" -o /out/arisu ./cmd/arisu

FROM alpine:3.20
RUN adduser -D -u 10001 arisu && apk add --no-cache ca-certificates tzdata
WORKDIR /app
COPY --from=build /out/arisu /app/arisu
# uploads 目录要预建并归 arisu：命名卷首次挂载会继承镜像内该路径的属主，
# 不预建的话卷归 root，uid 10001 写不进——头像/附件全 500
RUN mkdir -p /app/uploads && chown arisu:arisu /app/uploads
USER arisu
EXPOSE 8080
ENTRYPOINT ["/app/arisu"]
