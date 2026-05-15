from PIL import Image

uploads = '.'
output  = './wear_rainbow.png'

# Rainbow order: red, orange, yellow, green, teal, blue, purple, pink
order = ['wear_red','wear_orange','wear_yellow','wear_green','wear_teal','wear_blue','wear_purple','wear_pink']
sources = [Image.open(f'{uploads}/{f}.png') for f in order]

W, H = 480, 480
slice_w = W // 8  # exactly 60

result = Image.new('RGB', (W, H))
for i, img in enumerate(sources):
    x0 = i * slice_w
    strip = img.crop((x0, 0, x0 + slice_w, H))
    result.paste(strip, (x0, 0))

result.save(output)
