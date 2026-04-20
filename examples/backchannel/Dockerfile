FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev

FROM node:20-alpine
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY index.js ./

ENV PORT=3000
EXPOSE 3000

USER node
CMD ["node", "index.js"]
