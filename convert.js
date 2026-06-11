const fs = require('fs');
const out = fs.readFileSync('out.txt', 'utf16le');
fs.writeFileSync('out_utf8.txt', out, 'utf8');
