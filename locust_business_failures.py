from locust import HttpUser, task, between

class BusinessFailureUser(HttpUser):
    wait_time = between(1, 3)

    @task(3)
    def bad_login(self):
        with self.client.post("/orders/login", data={
            "username": "baduser",
            "password": "wrongpass"
        }, name="EXPECTED_FAIL bad login", catch_response=True) as r:
            if r.status_code in [200, 302]:
                r.success()
            else:
                r.failure(f"Unexpected bad login status {r.status_code}")

    @task(2)
    def empty_checkout(self):
        with self.client.post("/orders/checkout", name="EXPECTED_FAIL empty checkout", catch_response=True) as r:
            if r.status_code in [200, 302, 400, 409]:
                r.success()
            else:
                r.failure(f"Unexpected empty checkout status {r.status_code}")

    @task(2)
    def invalid_sku(self):
        self.client.post("/orders/login", data={
            "username": "admin",
            "password": "admin"
        }, name="Login success")

        with self.client.post("/orders/cart/add", data={
            "sku": "BAD-SKU-DOES-NOT-EXIST",
            "qty": "1"
        }, name="EXPECTED_FAIL invalid SKU", catch_response=True) as r:
            if r.status_code in [200, 302, 400, 404, 409, 500]:
                r.success()
            else:
                r.failure(f"Unexpected invalid SKU status {r.status_code}")

    @task(1)
    def bad_quantity(self):
        self.client.post("/orders/login", data={
            "username": "admin",
            "password": "admin"
        }, name="Login success")

        with self.client.post("/orders/cart/add", data={
            "sku": "SKU1",
            "qty": "999999"
        }, name="EXPECTED_FAIL bad quantity", catch_response=True) as r:
            if r.status_code in [200, 302, 400, 409, 500]:
                r.success()
            else:
                r.failure(f"Unexpected bad quantity status {r.status_code}")
