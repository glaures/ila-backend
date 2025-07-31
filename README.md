docker network create ilanet
docker run --name ila-db --net ilanet -v /Users/Guido/mysql/8/ila:/var/lib/mysql:delegated -e MYSQL_ROOT_PASSWORD=pw -p 3307:3306 --restart always -d mysql:8.0