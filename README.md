# vas3k_music_bot

This bot uses [Odesil API](https://odesli.co/) to parse music links.
It also creates a playlist in Spotify every month and automatically adds all songs there.


To run the bot you should fill in all variables in `.env` and run `make deploy-test`
To recreate the bot you should use `make redeploy-test`.

To run in production you should create `production.env` and run `make deploy-prod`. To deploy prodaction again use `make redeploy-prod`.


