FROM socialengine/nginx-spa:latest
LABEL org.opencontainers.image.source https://github.com/idlab-discover/obelisk

COPY dist/ /app
COPY default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
