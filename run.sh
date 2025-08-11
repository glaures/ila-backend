#!/bin/bash
git pull
docker build -t ila/ila-backend .
docker stop ila-backend
docker rm ila-backend
docker run -p 8094:8080 \
--env SPRING_PROFILES_ACTIVE=production \
--name ila-backend \
--restart unless-stopped \
--net spaboot \
-d \
-v "$(pwd)/application-production.properties":/config/application-production.properties:ro \
ila/ila-backend