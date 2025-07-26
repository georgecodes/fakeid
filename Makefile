test:
	@mvn clean test

.PHONY: verify
verify: test itests

buildlocal:
	@docker buildx build --load --platform=linux/arm64,linux/amd64 -t fakeid .

compose:
	@docker compose -f docker-compose-tests.yml down
	@docker compose -f docker-compose-tests.yml rm
	@docker compose -f docker-compose-tests.yml build
	@docker compose -f docker-compose-tests.yml up

itests:
	@docker compose -f docker-compose-tests.yml down
	@docker compose -f docker-compose-tests.yml rm
	@docker compose -f docker-compose-tests.yml build
	@docker compose -f docker-compose-tests.yml up -d
	@mvn clean test -P IT
	@docker compose -f docker-compose-tests.yml down



