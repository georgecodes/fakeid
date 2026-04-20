#!/bin/sh
set -e
envsubst '${ISSUER} ${CLIENT_ID}' \
  < /usr/share/nginx/html/config.js.template \
  > /usr/share/nginx/html/config.js
exec nginx -g 'daemon off;'
