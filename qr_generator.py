import qrcode
import barcode
from barcode.writer import ImageWriter
from PIL import Image, ImageDraw, ImageFont

import sys

# Item details
item_id = sys.argv[1] if len(sys.argv) > 1 else "GLD000123"

# -------------------------
# Generate QR Code
# -------------------------
qr = qrcode.QRCode(
    version=1,
    box_size=8,
    border=2
)

qr.add_data(item_id)
qr.make(fit=True)

qr_img = qr.make_image(fill_color="black", back_color="white")
qr_img = qr_img.convert("RGB")

# -------------------------
# Generate Barcode
# -------------------------
code128 = barcode.get(
    'code128',
    item_id,
    writer=ImageWriter()
)

barcode_file = code128.save("temp_barcode")

barcode_img = Image.open(barcode_file)

# -------------------------
# Create Label
# -------------------------
label_width = 600
label_height = 500

label = Image.new("RGB", (label_width, label_height), "white")

# Resize images
qr_img = qr_img.resize((220, 220))
barcode_img = barcode_img.resize((500, 120))

# Paste QR
label.paste(qr_img, (190, 20))

# Paste Barcode
label.paste(barcode_img, (50, 270))

# Draw Text
draw = ImageDraw.Draw(label)

try:
    font = ImageFont.truetype("arial.ttf", 24)
except:
    font = ImageFont.load_default()

draw.text((220, 240), item_id, fill="black", font=font)
draw.text((220, 410), item_id, fill="black", font=font)

# Save final label
output_filename = f"{item_id}_label.png"
label.save(output_filename)

print(f"Label created successfully: {output_filename}")