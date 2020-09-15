import cv2
import numpy as np

# Load pretrained model
face_haar_det = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')


def face_detector(img):
    global face_haar_det
    # Convert to gray
    img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    # Detect faces
    bboxes = face_haar_det.detectMultiScale(img_gray, 1.1, 4)
    # Draw bboxes
    faces = np.copy(img)
    for x, y, w, h in bboxes:
        cv2.rectangle(faces, (x, y), (x + w, y + h), (0, 255, 0), 2)
    return faces


if __name__ == '__main__':
    # Webcam as the video source
    vid = cv2.VideoCapture(0)
    # Read the first frame
    ret, frm = vid.read()

    while ret:
        # Detect faces and draw bboxes on the frame
        faces = face_detector(frm)
        # Flip and show the result
        frm = np.flip(faces, 1)
        cv2.imshow('Face detection', faces)
        # 'q' == stop
        key = cv2.waitKey(1)
        if key == ord('q'):
            break
        # Read the next frame
        ret, frm = vid.read()

    vid.release()
