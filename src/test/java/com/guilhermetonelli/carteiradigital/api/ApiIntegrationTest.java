package com.guilhermetonelli.carteiradigital.api;

import com.guilhermetonelli.carteiradigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa a API de ponta a ponta por HTTP real: segurança, validações e formato dos erros. */
class ApiIntegrationTest extends PostgresIntegrationTest {

    private record Account(String email, String walletId, String token) {
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("campo '%s' em: %s", field, json).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> register(String email) throws Exception {
        String body = """
                {"name":"Teste","email":"%s","password":"senha1234"}
                """.formatted(email);
        return send("POST", "/users", null, body);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return send("POST", "/auth/login", null, body);
    }

    private Account createAccount() throws Exception {
        String email = UUID.randomUUID() + "@teste.com";
        HttpResponse<String> created = register(email);
        assertThat(created.statusCode()).isEqualTo(201);
        HttpResponse<String> logged = login(email, "senha1234");
        assertThat(logged.statusCode()).isEqualTo(200);
        return new Account(email, extract(created.body(), "walletId"), extract(logged.body(), "accessToken"));
    }

    private static String amount(String value) {
        return "{\"amount\":" + value + "}";
    }

    @Test
    @DisplayName("rotas protegidas exigem um token válido; o health é público")
    void protectedRoutesRequireToken() throws Exception {
        assertThat(send("GET", "/wallets/me", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/wallets/me", "token-invalido", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("login com senha errada ou e-mail inexistente dá 401")
    void invalidCredentialsAreRejected() throws Exception {
        Account ana = createAccount();

        assertThat(login(ana.email(), "senha-errada").statusCode()).isEqualTo(401);
        assertThat(login(UUID.randomUUID() + "@teste.com", "senha1234").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("e-mail repetido dá 409 e dados inválidos dão 400")
    void registrationValidation() throws Exception {
        String email = UUID.randomUUID() + "@teste.com";

        assertThat(register(email).statusCode()).isEqualTo(201);
        assertThat(register(email).statusCode()).isEqualTo(409);

        String invalid = "{\"name\":\"\",\"email\":\"nao-e-email\",\"password\":\"123\"}";
        assertThat(send("POST", "/users", null, invalid).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("fluxo completo: depositar, transferir e consultar saldo e extrato")
    void fullFlow() throws Exception {
        Account ana = createAccount();
        Account bruno = createAccount();

        assertThat(send("POST", "/wallets/" + ana.walletId() + "/deposit", ana.token(), amount("100.00")).statusCode())
                .isEqualTo(201);

        String transferBody = """
                {"targetWalletId":"%s","amount":40.00}
                """.formatted(bruno.walletId());
        assertThat(send("POST", "/wallets/" + ana.walletId() + "/transfer", ana.token(), transferBody).statusCode())
                .isEqualTo(201);

        HttpResponse<String> brunoWallet = send("GET", "/wallets/me", bruno.token(), null);
        assertThat(brunoWallet.statusCode()).isEqualTo(200);
        assertThat(brunoWallet.body()).contains("40");

        HttpResponse<String> statement = send("GET", "/wallets/" + ana.walletId() + "/transactions", ana.token(), null);
        assertThat(statement.statusCode()).isEqualTo(200);
        assertThat(statement.body()).contains("\"totalElements\":2").contains("TRANSFER").contains("DEPOSIT");
    }

    @Test
    @DisplayName("um usuário não acessa nem opera a carteira de outro (403)")
    void cannotAccessAnotherUsersWallet() throws Exception {
        Account ana = createAccount();
        Account bruno = createAccount();

        assertThat(send("GET", "/wallets/" + bruno.walletId(), ana.token(), null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/wallets/" + bruno.walletId() + "/transactions", ana.token(), null).statusCode())
                .isEqualTo(403);
        assertThat(send("POST", "/wallets/" + bruno.walletId() + "/withdraw", ana.token(), amount("1.00")).statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("requisições inválidas voltam com o código e a mensagem corretos")
    void invalidRequestsReturnClientErrors() throws Exception {
        Account ana = createAccount();
        String wallet = "/wallets/" + ana.walletId();

        assertThat(send("POST", wallet + "/deposit", ana.token(), amount("10.555")).statusCode()).isEqualTo(400);
        assertThat(send("POST", wallet + "/deposit", ana.token(), amount("0")).statusCode()).isEqualTo(400);
        assertThat(send("POST", wallet + "/deposit", ana.token(), "isto nao e json").statusCode()).isEqualTo(400);

        HttpResponse<String> insufficient = send("POST", wallet + "/withdraw", ana.token(), amount("999.00"));
        assertThat(insufficient.statusCode()).isEqualTo(422);
        assertThat(insufficient.body()).contains("Saldo insuficiente");

        String toUnknown = """
                {"targetWalletId":"%s","amount":1.00}
                """.formatted(UUID.randomUUID());
        assertThat(send("POST", wallet + "/transfer", ana.token(), toUnknown).statusCode()).isEqualTo(404);

        String toSelf = """
                {"targetWalletId":"%s","amount":1.00}
                """.formatted(ana.walletId());
        assertThat(send("POST", wallet + "/transfer", ana.token(), toSelf).statusCode()).isEqualTo(400);

        assertThat(send("GET", wallet + "/transactions?type=PIX", ana.token(), null).statusCode()).isEqualTo(400);
        assertThat(send("GET", wallet + "/transactions?size=1000", ana.token(), null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/wallets/isto-nao-e-uuid", ana.token(), null).statusCode()).isEqualTo(400);
    }
}
