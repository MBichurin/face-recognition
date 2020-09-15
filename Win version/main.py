import cv2
import numpy as np
import dlib

# Load pretrained models
face_haar_det = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')
face_hog_det = dlib.get_frontal_face_detector()
landmarks_det = dlib.shape_predictor('shape_predictor_68_face_landmarks.dat')


def haar_face_detector(img):
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


def dlib_face_detector(img):
    global face_hog_det, landmarks_det
    faces = np.copy(img)
    # Detect faces
    bboxes = face_hog_det(img, 1)
    # Loop through the faces
    for bbox in bboxes:
        # Get landmarks and convert to a numpy array
        landmarks = shape_to_np(landmarks_det(img, bbox))
        # Draw bbox and landmarks
        cv2.rectangle(faces, (bbox.left(), bbox.top()), (bbox.right(), bbox.bottom()), (0, 255, 0), 2)
        for x, y in landmarks:
            if x < faces.shape[1] and y < faces.shape[0]:
                cv2.circle(faces, (x, y), 1, (0, 255, 0), -1)

    return faces


def shape_to_np(landmarks):
    np_landmarks = np.zeros((68, 2), dtype=np.uint32)
    for i in range(68):
        np_landmarks[i] = (landmarks.part(i).x, landmarks.part(i).y)

    return np_landmarks


if __name__ == '__main__':
    # Webcam as the video source
    vid = cv2.VideoCapture(0)
    # Read the first frame
    ret, frm = vid.read()

    while ret:
        # Detect faces and draw bboxes on the frame
        faces = dlib_face_detector(frm)
        # Flip and show the result
        faces = np.flip(faces, 1)
        cv2.imshow('Face detection', faces)
        # 'q' == stop
        key = cv2.waitKey(1)
        if key == ord('q'):
            break
        # Read the next frame
        ret, frm = vid.read()

    cv2.destroyAllWindows()
    vid.release()
