import cv2
import numpy as np
from PIL import Image
from facenet_pytorch import MTCNN, InceptionResnetV1
import json
import torch

# Load pretrained models
face_mtcnn_det = MTCNN(image_size=160, margin=40)
facenet = InceptionResnetV1(pretrained='vggface2').eval()
# face_haar_det = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')
# face_hog_det = dlib.get_frontal_face_detector()
# landmarks_det = dlib.shape_predictor('shape_predictor_68_face_landmarks.dat')


def find_and_describe_faces(img):
    global face_mtcnn_det
    # Convert to PIL image
    img_pil = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img_pil = Image.fromarray(img_pil)
    # Detect faces and get landmarks
    bboxes, probs, landmarks_list = face_mtcnn_det.detect(img_pil, landmarks=True)

    if bboxes is None:
        return None, None

    bboxes = bboxes.astype(int)
    landmarks_list = landmarks_list.astype(int)

    # Make a batch from all the faces on the current frame
    faces = torch.zeros((len(bboxes), 3, 160, 160), dtype=torch.float)
    for face_ind, (bbox, landmarks) in enumerate(zip(bboxes, landmarks_list)):
        # Transform coordinates into bbox-relative
        landmarks = [((x - bbox[0]) * 160 // (bbox[2] - bbox[0]),
                      (y - bbox[1]) * 160 // (bbox[3] - bbox[1])) for (x, y) in landmarks]
        # Extract and align face
        face = img[bbox[1]:bbox[3], bbox[0]:bbox[2], :]
        if face.shape[0] != 0 and face.shape[1] != 0:
            face = cv2.resize(face, (160, 160))
            aligned_face = align_face(face, landmarks)
            # Convert to tensor
            face = cv2.cvtColor(aligned_face, cv2.COLOR_BGR2RGB)
            # Write to the faces array
            faces[face_ind] = torch.from_numpy(face).permute(2, 0, 1).float()

            # MTCNN PREWHITEN() MAY BE HELPFUL

    # Get embedding vectors of the faces
    embeddings = facenet(faces)

    return bboxes, embeddings


def align_face(img, landmarks, face_size=(160, 160), l_eye=(0.25, 0.4)):
    dX, dY = (landmarks[1][0] - landmarks[0][0], landmarks[1][1] - landmarks[0][1])
    # Rotation angle
    eyeline_angle = np.degrees(np.arctan2(dY, dX))
    # Current and desired distance between eyes and scale
    cur_eyes_dist = np.sqrt(dX * dX + dY * dY)
    new_eyes_dist = (1 - l_eye[0] * 2) * face_size[0]
    scale = new_eyes_dist / cur_eyes_dist
    # Center of the eyeline
    eyes_center = ((landmarks[0][0] + landmarks[1][0]) // 2, (landmarks[0][1] + landmarks[1][1]) // 2)
    # Get the rotation matrix
    Matrix = cv2.getRotationMatrix2D(eyes_center, eyeline_angle, scale)
    # Translation to move the eyes_center where it's supposed to be
    Matrix[0, 2] += (face_size[0] / 2 - eyes_center[0])
    Matrix[1, 2] += (face_size[1] * l_eye[1] - eyes_center[1])
    # Affine transformation
    img_aligned = cv2.warpAffine(img, Matrix, face_size, flags=cv2.INTER_CUBIC)
    return img_aligned


def shape_to_np(landmarks):
    np_landmarks = np.zeros((68, 2), dtype=np.uint32)
    for i in range(68):
        np_landmarks[i] = (landmarks.part(i).x, landmarks.part(i).y)

    return np_landmarks


SavedFaces = {}


def face_recognition(new_vec):
    min_dist, closest_id = None, None
    threshold = 1.242
    # Iterate through saved identities
    for saved_id in SavedFaces:
        saved_vec = SavedFaces[saved_id]
        loc_dist = L2_sq(new_vec, saved_vec)
        if min_dist is None or min_dist < loc_dist:
            min_dist = loc_dist
            closest_id = saved_id

    if min_dist is None or min_dist > threshold:
        return None

    return closest_id


def L2_sq(A, B):
    np.asarray(A), np.asarray(B)
    return np.sum(np.power(A - B, 2))


def write_saved_faces(filename):
    with open(filename + '.json', 'w') as file:
        json.dump(SavedFaces, file)


def read_saved_faces(filename):
    global SavedFaces
    with open(filename + '.json') as file:
        SavedFaces = json.load(file)


if __name__ == '__main__':
    # Read saved identities
    read_saved_faces('saved_faces')
    # Webcam as the video source
    vid = cv2.VideoCapture(0)
    # Read and flip the first frame
    ret, frm = vid.read()
    frm = np.flip(frm, 1)
    # Current mode
    mode = ord('1')
    # Number of shots for a new identity, current index of a shot, vector and name of a new identity
    shots_n = 5
    shot_ind = 0
    new_vec = None
    face_name = None

    while ret:
        # Get bboxes and embeddings of faces on the current frame
        bboxes, embeddings = find_and_describe_faces(frm)
        # Image to show
        img_show = np.copy(frm)

        # Check what key's pressed
        key = cv2.waitKey(1)
        if key == ord('1') or key == ord('2'):
            mode = key
        # Quit
        if key == ord('q'):
            break

        if bboxes is not None:
            # Recognition mode
            if mode == ord('1'):
                for bbox, vec in zip(bboxes, embeddings):
                    # Convert embedding from tensor to numpy
                    vec = vec.detach().numpy()
                    # Recognize + set color
                    id = face_recognition(vec)
                    color = (0, 0, 255) if id is None else (0, 255, 0)
                    # Draw bbox and sign the face
                    cv2.rectangle(img_show, (bbox[0], bbox[1]), (bbox[2], bbox[3]), color, 2)
                    cv2.putText(img_show, '???' if id is None else id,
                                (bbox[0], bbox[1] - 5), cv2.QT_FONT_NORMAL, 0.5, color, 1)

            # Save face mode
            if mode == ord('2'):
                if face_name is None:
                    # New identity's name
                    face_name = input('Put your name: ')

                # Get bbox and embedding of the first face
                bbox, vec = bboxes[0], embeddings[0]
                # Draw bbox and sign the face
                cv2.rectangle(img_show, (bbox[0], bbox[1]), (bbox[2], bbox[3]), (255, 0, 0), 2)
                cv2.putText(img_show, face_name, (bbox[0], bbox[1] - 5), cv2.QT_FONT_NORMAL, 0.5, (255, 0, 0), 1)

                # Make a shot
                if key == ord(' '):
                    # Whiten the frame
                    img_show += 100
                    if shot_ind == 0:
                        new_vec = vec
                    elif shot_ind < shots_n:
                        new_vec.add_(vec)
                    shot_ind += 1

                    if shot_ind == shots_n:
                        # Save embedding for the new identity as an average of vectors of made shots
                        SavedFaces[face_name] = (new_vec / shots_n).tolist()
                        print(face_name + ' identity\'s saved')
                        # Set shot index back to 0, mode to recognition, face_name to None
                        shot_ind = 0
                        mode = ord('1')
                        face_name = None

        cv2.imshow('Face Recognition', img_show)

        # Read and flip the next frame
        ret, frm = vid.read()
        frm = np.flip(frm, 1)

    cv2.destroyAllWindows()
    vid.release()

    # Remember saved identities
    write_saved_faces('saved_faces')


# def haar_face_detector(img):
#     global face_haar_det
#     # Convert to gray
#     img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
#     # Detect faces
#     bboxes = face_haar_det.detectMultiScale(img_gray, 1.1, 4)
#     # Draw bboxes
#     faces = np.copy(img)
#     for x, y, w, h in bboxes:
#         cv2.rectangle(faces, (x, y), (x + w, y + h), (0, 255, 0), 2)
#
#     return faces
#
#
# def dlib_face_detector(img):
#     global face_hog_det, landmarks_det
#     faces = np.copy(img)
#     # Detect faces
#     bboxes = face_hog_det(img, 1)
#     # Loop through the faces
#     for bbox in bboxes:
#         # Get landmarks and convert to a numpy array
#         landmarks = shape_to_np(landmarks_det(img, bbox))
#         # Draw bbox and landmarks
#         cv2.rectangle(faces, (bbox.left(), bbox.top()), (bbox.right(), bbox.bottom()), (0, 255, 0), 2)
#         for x, y in landmarks:
#             if x < faces.shape[1] and y < faces.shape[0]:
#                 cv2.circle(faces, (x, y), 1, (0, 255, 0), -1)
#
#     return faces
#
#
# def mtcnn_face_detector(img):
#     global face_mtcnn_det
#     faces = np.copy(img)
#     # Convert to PIL image
#     img_pil = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
#     img_pil = Image.fromarray(img_pil)
#
#     # Detect faces
#     bboxes, probs, landmarks_list = face_mtcnn_det.detect(img_pil, landmarks=True)
#
#     # # Get cropped face
#     # img_crop = face_mtcnn_det(img_pil)
#     # if img_crop is not None:
#     #     img_embedding = facenet(img_crop.unsqueeze(0))
#
#     # Loop through the faces
#     if bboxes is not None:
#         for face_ind, (bbox, landmarks) in enumerate(zip(bboxes, landmarks_list)):
#             bbox = bbox.astype(int)
#             landmarks = landmarks.astype(int)
#             # Draw bbox and landmarks
#             cv2.rectangle(faces, (bbox[0], bbox[1]), (bbox[2], bbox[3]), (0, 255, 0), 2)
#             for i, (x, y) in enumerate(landmarks):
#                 if x < faces.shape[1] and y < faces.shape[0]:
#                     cv2.circle(faces, (x, y), 2, (0, 255, 0), -1)
#
#             # Transform coordinates into bbox-relative
#             landmarks = [((x - bbox[0]) * 160 // (bbox[2] - bbox[0]),
#                           (y - bbox[1]) * 160 // (bbox[3] - bbox[1])) for (x, y) in landmarks]
#             # Extract and align face
#             face = img[bbox[1]:bbox[3], bbox[0]:bbox[2], :]
#             if face.shape[0] != 0 and face.shape[1] != 0:
#                 face = cv2.resize(face, (160, 160))
#                 aligned_face = align_face(face, landmarks)
#                 cv2.imshow('Aligned Face ' + str(face_ind), aligned_face)
#
#     return faces