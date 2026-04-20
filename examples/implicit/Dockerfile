FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Replace the baked-in local dev config with a template for runtime substitution
RUN mv /usr/share/nginx/html/config.js /usr/share/nginx/html/config.js.template \
 && sed -i \
      "s|'http://localhost:8091'|\'\${ISSUER}\'|g; s|'client1'|\'\${CLIENT_ID}\'|g" \
      /usr/share/nginx/html/config.js.template

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
