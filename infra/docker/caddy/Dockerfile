# デモ環境（ADR-0015）の入口。HTTPS を受けて frontend に渡す。
# 許可した IP だけを受け付けるため、証明書は Route 53 の DNS で持ち主を確かめて取る（DNS-01）。そのためのモジュールを入れて作る
FROM caddy:2.11.7-builder AS build
RUN xcaddy build --with github.com/caddy-dns/route53@v1.6.2

FROM caddy:2.11.7
COPY --from=build /usr/bin/caddy /usr/bin/caddy
COPY Caddyfile /etc/caddy/Caddyfile
