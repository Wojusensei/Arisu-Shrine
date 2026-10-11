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

RUN mkdir -p /app/uploads && chown arisu:arisu /app/uploads
USER arisu
EXPOSE 8080
ENTRYPOINT ["/app/arisu"]
