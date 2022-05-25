# How to authenticate?

Before you can use any of the Obelisk APIs, you will need to authenticate the user or your client itself. 
After this authentication step, you will be able to acquire an Access Token that can be used to access the Obelisk APIs.

## Creating a Client

The first thing you will need is a [Client](/concepts#clients). You can create a client yourself, using the [{{extra.catalog.name}}]({{extra.catalog.url}}).
You can [create a personal client]({{extra.catalog.url}}/my/clients) or - if you are part of a [Team](/concepts#Teams) - a team client. Both have the same functionality.

All your personal clients however will share a common personal usage limit[^1] pool,
whereas your team clients will each get an individual instance of the "team usage plan"-granted usage limits pool.

!!! tip
    **Team clients are not automatically better!** A Team usage plan might be very restrictive compared to your personal usage limit shared by all your personal clients.

**Client options: confidential, public, authenticate-as-user, authenticate-as-client**

More important are the options you assign to your client. They determine the type of client you have, and the required authentication steps. Follow the table below to understand what the options mean.

 option             | value  | description
------------------- | ------ | --------------
 confidential       | yes    | Confidential clients don't expose their code publicly, they are running in  safe environment. This means they can keep a `client_secret` hidden in the code. 
 confidential       | no     | Public clients expose their code publicly, or can not be trusted to not be manipulated, hence they **cannot** keep a `client_secret` hidden. They must use the [PKCE extension](#pkce-extension) to login with a `code_challenge`.
 authenticate users | yes    | When your client facilitates users to access their content from Obelisk, then your app is said to `authenticate users`. This means a user will be redirected to Obelisk Login, and you client get authorization on behalf of the users to fetch their content.
 authenticate users | no     | When your client only needs to access content tied to the client itself, then it will not `authenticate users`.

!!! warning
    Native Android and iOS Apps are **not** running in a safe environment, so they are **not confidential**. They are distributed and run on phones that you don't have any control over. They can easily be manipulated with man-in-the-middle style attacks.
    **They are public clients** and require the use of the [PKCE Extension protocol](#pkce-extension).

The following table lists some use cases and what options the clients need.

confidential | authenticate users   | client type                                           |  examples
------------ | -------------------- | ----------------------------------------------------- | -------------
 yes         | yes                  | Backend-hosted client[^2] using Oblx User's content       | eg. Java Application server hosting a user-based web-application, Python Flask user-based server-side web-application
 yes^(*)^         | no^(*)^                   | Backend-hosted client using it's own data             | eg. Backend Java application sending data, Python backend application sending data, Backend Cron-timed bash script analysing data batches
 no          | yes                  | Client-side web application using Oblx User's content | eg. User-based dashboard Single Page Application, {{extra.catalog.name}}, Native Android or iOS application that uses User data.
 no     | no              | Client-side web application using it's own data       | eg. Client-side Javascript web-application consuming it's own data, Client built in to Native Android or iOS applications that sends data.

!!! info
    ^(*)^ A confidential client using its own data can directly skip ahead to [requesting an Access Token](#step-2-token).

In all but one of the cases above, you will have to follow a 2-step procedure to get an Access Token. **(The exception is the confidential+own-data use case marked with ^(*)^)**

The [first step](#step-1-authentication) will see you getting a *proof of authentication* in the form of an authorization `code`.
The [second step](#step-2-token) will allow you to exchange that *proof of authentication* for a Access Token.  

[^1]: Personal usage limits are calculated based on the personal usage limits that apply to your user and any user usage limits granted by usage plans of Teams that you are a member of.
[^2]: These clients are running in a safe environment within you organization. This means a typical backend server running on your own premise, or on a cloud environment only your organization has access to. A mobile phone **is not** a safe environment.

## Step 1: Authentication
To initiate a login, you need to point the User Agent (eg. browser) to the Authentication URL. This will open the Obelisk Authentication page for users to log in.
In case you have a public client that needs to authenticate by itself, you just GET the URL in you code, it will immediately respond with an authorization `code` on your registered `redirect_uri`.

After user login, the `redirect_uri` will also be called with a proper authorization `code` added to that `redirect_uri` as a query parameter along with the state that you passed in the initial request.

> https://redirect_uri`?code=`afjAae4Qsde3qWS2i`&state=`L2hvbWU

??? tip
    This state is useful if you want to navigate back to a certain location of your application once authentication is over.

If you are using a non-confidential client, you are **required** to use the PKCE Extension during the authentication procedure. This is a secure alternative to using a client_secret. Please read up on how to do this in the [PKCE Extension](#pkce-extension) section below.

!!! important 
    Technical information on how to construct this Authentication URL can be found in the [{{extra.apidocs.name}}]({{extra.apidocs.url}}#/reference/auth-api/authentication/get-authentication-url).

## Step 2: Token
You now have a *proof of authentication*, either as an authorization `code` retrieved in [Step 1](#step-1-authentication) or in the form of a _client credential pair_ (`clientId` and `clientSecret`) for confidential clients using their own data.
You can now send this *proof of authentication* to the [Token endpoint]({{extra.apidocs.url}}#/reference/auth-api/token) to retrieve an Access Token as described below.

* In all but one case, this will require you to set the `grant_type` to `authorization_code` to send you `code` in the request. 
* Only confidential clients acting on their own behalf will have skipped the [authentication step](#step-1-authentication) and will have to use their `client_id` and `client_password` as *proof of authentication*.
  They will have to set the `grant_type` to `client_credentials` and add the Base64 encoded string of the `client_id:client_secret` pair in the Authorization header like this:
  > Authorization: Basic `base64EncodedString`
  
If successful, a JSON object is returned. It contains a few keys, one of them (`access_token`) is your Access Token for the Obelisk APIs. 
To use the APIs add the Authorization Bearer header with the token to each request:
> Authorization: Bearer `yourAccessToken`

You will also get an IDToken, this is a JWT. It is digitally signed so the Obelisk backend can check if it has been tampered with.
It is primarily used to customize your client to some of the user's profile details.

*Response:*
```json
{
  "token_type": "bearer",
  "access_token" : "XNQNtuEZDfarzeDN",
  "id_token" : "eyJraWQiOiI4MWQ5MjhjYi1hNjQ4LTQyMDktYjk4OC00NjE0NDY2YTYyZTUiLCJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJjNjA1YjQ2OGYzZGQ1MTY2NmJjOTM4MGU5IiwibmFtZSI6IlB1YmxpY0FzSXRzZWxmIiwiZW1haWwiOiJ0ZXN0ZXJAb2JseC5pbyIsImlkcCI6ImxvY2FsIiwidHlwZSI6ImNsaWVudCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9hdXRoIiwiYXVkIjoiUHVibGljQXNJdHNlbGYiLCJleHAiOjE2MTkxODY1NzYsImp0aSI6Ik13YTJ0Tl9oZDNUaEhqZUdaOU5vTEEiLCJpYXQiOjE2MTY1OTQ1NzYsIm5iZiI6MTYxNjU5NDQ1Niwic2lkIjoiSVVUM0xITzhYRFl4MGk2ZyJ9.fH-ZBytOI6rBrH-CCBixpIvmD-MGflELiz2Kn1qiGfRREOT6wpGrEmxueyYLmhJDJkLswrcCdhKgM747MvPB4rkoRVM4DlYpDKzl4mIsb0bua1hC-TkxG5aW8W9ZUNj1vMXotDDnLPQhWtOPFDxyum2qdu8OHzQmTENuo72ujuegF4irv4BahW33fVUk7YznSHmoRwjFGqtzcvPZCfpD30fe-Q42aTQlGKig7cavVx5wFs2FNljdv9ft41g5HoneRTdRIDtsjYtMbk3z4OSE9uFIlGFjReAsIOn0ZvRKlrbCBb027kDHjQn15ZJvaBNlpuJorINalsKgrFs6fCCYPw",
  "max_idle_time" : 3600,
  "expires_in" : 3600,
  "max_valid_time": 86400,
  "remember_me" : false
}

```

* `token_type`: The type of access_token. Helps to use it correctly.
* `access_token`: Your access_token to use as Bearer Token for Obelisk API calls
* `id_token`: Your id_token, used for customizing your application to some user values
* `max_idle_time`: Maximum time (in seconds) the access_token stays valid, if not being used on API calls.
* `expires_in`: Maximum time (in seconds) the access_token stays valid, if not being used on API calls. (alias for `max_idle_time`).
* `max_valid_time`: Maximum time (in seconds) the access_token stays valid, even if it is being used within `max_idle_time` windows.   
* `remember_me`: Tells your application if the users requested to remember his/her session or not. It is up to your own application to support this or not.


!!! important
    Technical information on the Token Endpoint can be found in the [{{extra.apidocs.name}}]({{extra.apidocs.url}}#/reference/auth-api/token).

## PKCE Extension
Since it is not safe to store a password in the code of a non-confidential client, this method makes use of the fact that we are executing a 2-step protocol.

Step by step:

1. Your client will construct a random string called the `code_verifier`
2. Your client will hash this string using SHA-256 and call the result the `code_challenge`
3. Your client will send the `code_challenge` and the `code_challenge_method` (`SHA-256`) as extra parameters with the authentication URL request.
4. The backend will store these and keep them for later.
5. When your client receives the authentication `code` and sends it back to the Token endpoint in [step 2](#step-2-token), it will send along the original `code_verifier`.
6. The backend will hash the received `code_verifier` with the method stored and compare it with your originally sent `code_challenge`.
7. If they match, the backend is sure that it was talking to the same client during this protocol and a man-in-the-middle-attack is prevented.

More on this extension can be read in the official [RFC 7636](https://tools.ietf.org/html/rfc7636).

### Creating a code_verifier
The recommendation in the [RFC](https://tools.ietf.org/html/rfc7636#appendix-A) goes as follows:
> **Appendix A.  Notes on Implementing Base64url Encoding without Padding**
> 
> This appendix describes how to implement a base64url-encoding
function without padding, based upon the standard base64-encoding
function that uses padding.
>
> To be concrete, example C# code implementing these functions is shown
below.  Similar code could be used in other languages.
>
>     static string base64urlencode(byte [] arg)
>     {
>       string s = Convert.ToBase64String(arg); // Regular base64 encoder
>       s = s.Split('=')[0]; // Remove any trailing '='s
>       s = s.Replace('+', '-'); // 62nd char of encoding
>       s = s.Replace('/', '_'); // 63rd char of encoding
>       return s;
>     }

**Here are some code examples on how to create a proper `code_verifier`.**

* Kotlin
```kotlin
private fun createCodeVerifier(): String {
    val oct32 = Random.nextBytes(32)
    val code_verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(oct32)
    return code_verifier
}
```
  
* Java
```java
private String createCodeVerifier() {
    Random rg = new Random();
    byte[] oct32 = new byte[32];
    rg.nextBytes(oct32);
    String code_verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(oct32);
    return code_verifier;
}
```
  
* TypeScript
```typescript
const cr = window.crypto;
const randomOctets = new Uint8Array(32);
cr.getRandomValues(randomOctets);
const codeVerifier = b64urlFromBuffer(randomOctets);
```


### Creating a code_challenge
The recommendation in the [RFC](https://tools.ietf.org/html/rfc7636#section-4.2) goes as follows:

> **4.2.  Client Creates the Code Challenge**
>  The client then creates a code challenge derived from the code
verifier...
>
>     S256
>        code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
> 
> If the client is capable of using "S256", it MUST use "S256", as
"S256" is Mandatory To Implement (MTI) on the server.

**Here are some code examples on how to properly calculate the `code_challenge` hash.**

* *Kotlin*
```kotlin

/** SHA-256 encoding. Using DigestEngine class from https://util.jodd.org/, but can be any SHA256 MessageDigest Engine */
fun S256(codeVerifier: String): ByteArray {
    return DigestEngine.sha256().digest(codeVerifier)
}

/** Base64url encoder without padding */
fun base64urlEncode(arg: ByteArray): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(arg);
}

val code_challenge = base64urlEncode(S256(codeVerifier))

```

* *Java*
```java
private byte[] S256(String codeVerifier) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] enc = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
    return enc;
}

private String base64urlEncode(byte[] arg) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(arg);
}

private String createCodeChallenge() {
    String code_challenge = null;
    try {
        code_challenge = base64urlEncode(S256("test"));
    } catch (NoSuchAlgorithmException ex) {
        // Wrong algorithm
    }
    return code_challenge;
}
```

* *TypeScript*
```typescript
private async getCodeChallenge(codeVerifider: string): Promise<string> {
    const codeChallenge = b64urlFromBuffer(await this.digestMessage(codeVerifier));
    return Promise.resolve(codeChallenge);
}

private async digestMessage(message: string) {
    const msgUint8 = new TextEncoder().encode(message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);
    return hashBuffer;
}
```

## Login to existing session
A user can login to his/her existing session by sending the `id_token`. If you want to support this in your client, you need to store the `id_token` of the user in browser storage. 

You can then send it along with the [authentication url](#step-1-authentication) as a query parameter `id_token`. 

The handling for your client is identical, the only difference is that the user will not be prompted with a login if there is 
a session still active on the backend.


--8<-- "snippets/glossary.md"