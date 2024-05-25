import { createRequire } from 'module';
const require = createRequire(import.meta.url);

const https = require('https');
const url = require('url');

export function handler(event, ctx, cb) {
    const whurl = "https://discord.com/api/webhooks/1242343906549698561/U6wfYOCxcSlmPmGpgkgrb5bujNJQOZBjqOWM_P1iyU1G360oJNRHZWj50tNSGu5HnAj5";
    const message = event["Records"][0]["body"];

    sendWebhook(message, whurl).then((result) => {
        cb(null, JSON.stringify(result));
    })
    .catch(error => cb(error));
}

function sendWebhook(message, wh) {
    return new Promise((resolve, reject) => {
        const whurl = url.parse(wh);
        const payload = JSON.stringify({
            //'username': 'SyncSketch',
            'content':  message
        });
        const options = {
            hostname: 'discordapp.com',
            headers: {'Content-type': 'application/json'},
            method: "POST",
            path: whurl.path,
        };
        console.log(whurl.path)
        var bufferData = '';
        const req = https.request(options, (res) => {
            res.on("data", (data) => {
                bufferData += data;
            });
            res.on("end",  () => {
                let data = {message: 'Webhook message sent!'};
                if(bufferData.length) {
                    data = JSON.parse(bufferData);
                }
                if(data.code) {
                    reject(JSON.stringify({statusCode:data.code, body:data.message}));
                } else {
                    resolve({statusCode: 200, body: data.message});
                }
            });
        });

        req.on("error", (error) => {
            reject(Error(error));
        });
        req.write(payload);
        req.end();
    });
}