import cv2
import numpy as np
import dlib
from PIL import Image
from facenet_pytorch import MTCNN, InceptionResnetV1

# Load pretrained models
face_haar_det = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')
face_hog_det = dlib.get_frontal_face_detector()
landmarks_det = dlib.shape_predictor('shape_predictor_68_face_landmarks.dat')
face_mtcnn_det = MTCNN(image_size=160, margin=40)
IncRes = InceptionResnetV1(pretrained='vggface2').eval()


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


def mtcnn_face_detector(img):
    global face_mtcnn_det
    faces = np.copy(img)
    # Convert to PIL image
    img_pil = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img_pil = Image.fromarray(img_pil)

    # Detect faces
    bboxes, probs, landmarks_list = face_mtcnn_det.detect(img_pil, landmarks=True)

    # # Get cropped face
    # img_crop = face_mtcnn_det(img_pil)
    # if img_crop is not None:
    #     img_embedding = IncRes(img_crop.unsqueeze(0))

    # Loop through the faces
    if bboxes is not None:
        for face_ind, (bbox, landmarks) in enumerate(zip(bboxes, landmarks_list)):
            # Draw bbox and landmarks
            cv2.rectangle(faces, (bbox[0], bbox[1]), (bbox[2], bbox[3]), (0, 255, 0), 2)
            for i, (x, y) in enumerate(landmarks):
                if x < faces.shape[1] and y < faces.shape[0]:
                    cv2.circle(faces, (x, y), 2, (0, 255, 0), -1)

            # Align face
            aligned_face = align_face(img, bbox, landmarks)
            cv2.imshow('Aligned Face ' + str(face_ind), aligned_face)

    return faces


def align_face(img, bbox, landmarks, face_size=(160, 160), l_eye=(0.35, 0.35)):
    dX, dY = (landmarks[1][0] - landmarks[0][0], landmarks[1][1] - landmarks[0][1])
    eyeline_angle = np.degrees(np.arctan2(dY, dX))
    cur_eyes_dist = np.sqrt(dX * dX + dY * dY)
    new_eyes_dist = (1 - l_eye[0] * 2) * face_size[0]
    scale = new_eyes_dist / cur_eyes_dist
    eyes_center = ((landmarks[0][0] + landmarks[1][0]) / 2, (landmarks[0][1] + landmarks[1][1]) / 2)
    Matrix = cv2.getRotationMatrix2D(eyes_center, eyeline_angle - 180, scale)
    # Translation to move the eyes_center where it's supposed to be
    Matrix[0, 2] = (face_size[0] / 2 - eyes_center[0])
    Matrix[1, 2] = (face_size[1] * l_eye[1] - eyes_center[1])


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
        faces = mtcnn_face_detector(frm)
        # faces = dlib_face_detector(frm)
        # faces = haar_face_detector(frm)

        # Flip and show the result
        if faces.shape != ():
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
