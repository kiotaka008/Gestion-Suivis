from locust import HttpUser, task, between

class AIUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        self.client.headers.update({
            "X-Internal-API-Key": "dev-local-key-change-me-1234567890"
        })

    @task(3)
    def health_check(self):
        self.client.get("/health")

    @task(1)
    def chat_request(self):
        self.client.post("/api/ai/chat", json={
            "messages": [{"role": "user", "content": "Bonjour"}]
        })
