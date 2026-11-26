# Build the React app against the API URL given at build time.
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npx tsc -b && npx vite build

# Serve the static files with nginx.
FROM nginx:1.29-alpine
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
