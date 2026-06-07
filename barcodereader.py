import cv2
from pyzbar.pyzbar import decode

cap = cv2.VideoCapture(0)

while True:
    _, frame = cap.read()

    for barcode in decode(frame):
        code = barcode.data.decode("utf-8")
        print("Scanned:", code)

    cv2.imshow("Scanner", frame)

    if cv2.waitKey(1) == 27:
        break

cap.release()
cv2.destroyAllWindows()