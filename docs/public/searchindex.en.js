var relearn_searchindex = [
  {
    "breadcrumb": "",
    "content": "Introduction If you’re ever developing an application which uses OIDC for login, it can get tiresome setting up an OIDC provider to use during development. Fake ID simplifies this by mocking out a basic OP for use in development. With a little configuration, your app can request and receive access and id tokens without you needing to authenticate and consent.\nUsage Typically you would run Fake ID in a docker container, or docker compose. Here is a sample docker-compose file\nversion: '3.8' services: myapplication: image: mydockerhub/app:v2 restart: always environment: - OIDC_ISSUER: \"http://fakeid:8091\" ports: - '8080:8080' fakeid: image: georgemc/fakeid:v0.0.1 environment: - FAKEID_ISSUER: \"http://fakeid:8091\" ports: - \"8091:8091\" The above will run the fake id container and configure it to issue tokens from http://fakeid:8091\nConfiguration There are a few ways to configure Fake ID. The simplest way is to simply start the container, and allow it to provide sensible defaults. Note that, as shown above, if you are running Fake ID in a docker environment, this may won’t work and you will at least need to override the issuer.\nConfiguration options are\nEnvironment variable Usage FAKEID_ISSUER This is the issuer used. It’s the base url for all operations, as well as the issuer claim in id tokens FAKEID_SIGNING_KEY This is a base64 encoded PEM-format RSA private key. It will be used for signing id tokens, and will be available on the JWKS uri of Fake ID FAKEID_SAMPLE_CLAIMS This is a template for returned id tokens. It can be either a full JWT or simply some base 64 encoded JSON FAKEID_SAMPLE_JWT This is merely an alias for FAKEID_SAMPLE_CLAIMS. Either can be used for either chosen format If you do not provide FAKEID_ISSUER it will default to http://localhost:8091\nIf you do not provide FAKEID_SIGNING_KEY one will be generated for you on startup. Note that this will change every time you start Fake ID, and if any relying parties are cacheing JWKS you may hit signature verification errors.\nIf you do not provide FAKEID_SAMPLE_CLAIMS your id tokens will have the usual necessary claims such as iss and aud, as well as a subject and name of “John C. Developer” and an email claim of “john@developer.com”\nGenerating these options You may generate the signing key however you please. Below are a few examples of how to\nOpenSSL for generating keys openssl genrsa -out keypair.pem 2048 base64 keypair.pem\nSome websites which will generate you a key https://cryptotools.net/rsagen\nhttps://emn178.github.io/online-tools/rsa/key-generator/\nhttps://www.devglan.com/online-tools/rsa-key-generator\nOnly the private key need be provided\nGenerating sample claims without using a JWT You may simply create a file\n{ \"name\": \"Dave Coder\", \"email\": \"dave@coding.com\", \"extraClaim\": \"Anything you want\" } and encode to base 64\ncat claims.json | base64 Or encode the JSON online at https://www.base64decode.org/\nGenerating a full JWT You may already have a sample JWT you have captured to serve as a template id token. Simply use that as-is. The signature and header will be disregarded\nYou can generate a JWT online at https://jwt.rocks/\nNote that providing a JWT will simply be used for some claims. The header and signature will be ignored. Certain claims will also be disregarded, including iss, iat, exp and aud.",
    "description": "Introduction If you’re ever developing an application which uses OIDC for login, it can get tiresome setting up an OIDC provider to use during development. Fake ID simplifies this by mocking out a basic OP for use in development. With a little configuration, your app can request and receive access and id tokens without you needing to authenticate and consent.\nUsage Typically you would run Fake ID in a docker container, or docker compose. Here is a sample docker-compose file",
    "tags": [],
    "title": "Fake ID",
    "uri": "/fakeid/index.html"
  },
  {
    "breadcrumb": "Fake ID",
    "content": "",
    "description": "",
    "tags": [],
    "title": "Categories",
    "uri": "/fakeid/categories/index.html"
  },
  {
    "breadcrumb": "Fake ID",
    "content": "",
    "description": "",
    "tags": [],
    "title": "Tags",
    "uri": "/fakeid/tags/index.html"
  }
]
