.PHONY: help up down build logs dev clean restart

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

up: ## Start all services in production mode
	docker-compose up -d

down: ## Stop all services
	docker-compose down

build: ## Build all services
	docker-compose build

logs: ## View logs from all services
	docker-compose logs -f

dev: ## Start all services in development mode with hot reload
	docker-compose -f docker-compose.dev.yml up

clean: ## Stop services and remove volumes (deletes database)
	docker-compose down -v

restart: ## Restart all services
	docker-compose restart

rebuild: ## Rebuild and restart all services
	docker-compose up -d --build
