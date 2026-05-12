from locust import HttpUser, task, between

class GoodBusinessUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        self.client.post("/orders/login", data={
            "username": "admin",
            "password": "admin"
        }, name="Login success")

    @task(4)
    def add_to_cart_and_view(self):
        self.client.post("/orders/cart/add", data={
            "sku": "SKU1",
            "qty": "1"
        }, name="Add item to cart")
        self.client.get("/orders/cart", name="View cart")

    @task(2)
    def card_checkout(self):
        self.client.post("/orders/cart/add", data={
            "sku": "SKU2",
            "qty": "1"
        }, name="Add item before card checkout")
        self.client.post("/orders/checkout", name="Card checkout")

    @task(1)
    def stripe_checkout_create_session(self):
        self.client.post("/orders/cart/add", data={
            "sku": "SKU1",
            "qty": "1"
        }, name="Add item before Stripe")

        self.client.post(
            "/orders/create-stripe-checkout",
            name="Create Stripe checkout",
            allow_redirects=False
        )

    @task(1)
    def health(self):
        self.client.get("/orders/health", name="Health")


class BadBusinessUser(HttpUser):
    wait_time = between(2, 5)

    @task(2)
    def bad_login(self):
        self.client.post("/orders/login", data={
            "username": "baduser",
            "password": "wrongpass"
        }, name="Bad login")

    @task(1)
    def empty_checkout(self):
        self.client.post("/orders/checkout", name="Empty checkout")
