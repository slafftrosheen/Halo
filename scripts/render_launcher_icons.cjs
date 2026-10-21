// Requires sharp. Rasterize the canonical artwork; never transcode binary assets as text.
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'docs/15-brand/v4/launcher/ic_launcher_full.svg');
(async () => {
  for (const [density, size] of Object.entries({mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192})) {
    const target = path.join(root, 'app/src/main/res', `mipmap-${density}`);
    await sharp(source).resize(size, size).webp({lossless: true}).toFile(path.join(target, 'ic_launcher.webp'));
    const mask = Buffer.from(`<svg width="${size}" height="${size}"><circle cx="${size/2}" cy="${size/2}" r="${size/2}" fill="white"/></svg>`);
    await sharp(source).resize(size, size).composite([{input: mask, blend: 'dest-in'}])
      .webp({lossless: true}).toFile(path.join(target, 'ic_launcher_round.webp'));
  }
})();
