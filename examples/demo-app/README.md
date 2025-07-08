# Demo Relying Party

There are two docker-compose files in this directory. 

## docker-compose-dev.yml

This one simply runs FakeID in a docker container so that you can run the included relying party application, 
which is a Spring Boot app, in your IDE, and have it authenticate against FakeID.

run
```bash
docker compose -f docker-compose-dev.yml up
```

Then run the DemoRelyingPartyApplication class and visit http://localhost:8080

## docker-compose-all.yml

This one runs both the relying party and FakeID in docker compose. Note the small 
tweaks required to allow FakeID to be accessed from both your browser and via the 
backchannel within docker compose

run
```bash
docker compose -f docker-compose-all.yml up
```

Then visit http://localhost:8080