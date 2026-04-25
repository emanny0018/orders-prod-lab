<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <title>Login - Manny Environment</title>
    <style>
        body {
            margin: 0;
            font-family: Arial, sans-serif;
            background: linear-gradient(rgba(0,0,0,.55), rgba(0,0,0,.55)),
                        url('images/office-bg.png') center/cover no-repeat fixed;
            color: white;
        }
        .login-wrap {
            text-align: center;
            margin-top: 90px;
        }
        h1 { font-size: 42px; margin-bottom: 5px; }
        .subtitle { font-size: 18px; margin-bottom: 25px; }
        input {
            padding: 12px;
            width: 210px;
            border-radius: 6px;
            border: none;
            margin: 5px;
        }
        button {
            padding: 12px 28px;
            border: none;
            border-radius: 6px;
            background: #ffffff;
            color: #111827;
            font-weight: bold;
            cursor: pointer;
        }
        button:hover { background: #e5e7eb; }
        .error {
            color: #fecaca;
            font-weight: bold;
            margin-bottom: 15px;
        }
        .hint {
            margin-top: 25px;
            color: #d1d5db;
            font-size: 14px;
        }
    </style>
</head>
<body>
<div class="login-wrap">
    <div style="letter-spacing:2px;">SECURE ACCESS</div>
    <h1>Login</h1>
    <p class="subtitle">Enter your details to access the orders environment.</p>

    <% if (request.getAttribute("error") != null) { %>
        <div class="error"><%= request.getAttribute("error") %></div>
    <% } %>

    <form action="login" method="post">
        <input type="text" name="username" placeholder="Username" required />
        <input type="password" name="password" placeholder="Password" required />
        <button type="submit">Sign In</button>
    </form>

    <div class="hint">Authorized users only</div>
</div>
</body>
</html>
