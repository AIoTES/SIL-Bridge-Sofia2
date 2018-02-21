if [ $# -eq 0 ]; then
    echo "Usage: $0 <platform-url>"
    echo "  platform-url: Example platform URL, accessible from intermw Docker container, e.g. http://172.17.0.1:4568"
    exit
fi
baseURL=$1

echo Registering client...
curl -X POST --header 'Content-Type: application/json' -d ' {"pullMessagesLimit": 5} ' "http://localhost:9080/mw.api.rest/api/intermw/client/myclient"
echo -e "\n"
sleep 2

echo Registering platform...
curl -X POST --header 'Content-Type: application/json' -d '{"id":{"id":"http://inter-iot.eu/example-platform1"},"type":{"typeId":"ExamplePlatform"},"capabilities":[],"baseURL":"$baseURL","name":"Example platform"}' "http://localhost:9080/mw.api.rest/api/intermw/platform/myclient"
echo -e "\n"
sleep 2

echo Registering thing...
curl -X POST --header 'Content-Type: application/json' -d '{"attributes":[],"platformId":{"id":"http://inter-iot.eu/example-platform1"},"thingId":{"id":"http://www.example.com/example-sensor"}}' "http://localhost:9080/mw.api.rest/api/intermw/thing/myclient"
echo -e "\n"
sleep 2

echo Subscribing to thing...
curl -X POST --header 'Content-Type: application/json' -d '{"attributes":[],"platformId":{"id":"http://inter-iot.eu/example-platform1"},"thingId":{"id":"http://www.example.com/example-sensor"}}' "http://localhost:9080/mw.api.rest/api/intermw/subscribe/myclient"
echo -e "\n"
sleep 2

echo Pulling messages...
curl -X POST --header 'Content-Type: application/json' "http://localhost:9080/mw.api.rest/api/intermw/pull/myclient/5"
echo -e "\n"
sleep 10

echo Pulling observation messages...
curl -X POST --header 'Content-Type: application/json' "http://localhost:9080/mw.api.rest/api/intermw/pull/myclient/5"
echo
