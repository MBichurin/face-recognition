# Real-time Face Recognition

## Face detection

Let’s look at the studied and tested methods of face detection.

### Haar cascade

The method is based on Haar features (Fig.1) and uses Adaboost that constructs a “strong” classifier as a linear combination of weighted “weak” classifiers.

![Fig. 1. Haar filters for face detection examples](https://docs.opencv.org/3.4/haar.png "Fig. 1. Haar filters for face detection examples")

*Fig. 1. Haar filters for face detection examples*

Cascade classification consists of ensembles of Haar features called stages. If a stage labels a region as positive, the classifier passes the region to the next stage. [The idea is in rejecting negative samples as fast as positive.](http://www.willberger.org/cascade-haar-explained/
)

We used [a pretrained model](https://github.com/opencv/opencv/blob/master/data/haarcascades/haarcascade_frontalface_default.xml) to implement it in the project.

### dlib face and landmarks detector

A Python package “dlib” contains a get_frontal_face_detector() method that returns a face detector based on HOG and Linear SVM.

The package also contains a landmark predictor. Using both led to a better performance with slightly higher time costs. We used [a pretrained model](https://github.com/AKSHAYUBHAT/TensorFace/blob/master/openface/models/dlib/shape_predictor_68_face_landmarks.dat) with [68 landmarks detecting](https://www.pyimagesearch.com/2017/04/03/facial-landmarks-dlib-opencv-python/), though there’s also a model pretrained on [detecting 5 landmarks](https://www.pyimagesearch.com/2018/04/02/faster-facial-landmark-detector-with-dlib/).

### MTCNN

The [Multi-Task Cascaded Convolutional Network](https://towardsdatascience.com/how-does-a-face-detection-program-work-using-neural-networks-17896df8e6ff) is a cascade of 3 neural networks: P-Net, R-Net and O-Net (see Fig. 2).

![Fig. 2. MTCNN Structure](https://miro.medium.com/max/875/1*ICM3jnRB1unY6G5ZRGorfg.png "Fig. 2. MTCNN Structure")

*Fig. 2. MTCNN Structure*

The algorithm is as follows:

1. build an image pyramid to make the performance scale-independent
2. run a 12*12 sliding window with a stride of 2 pixels on every scale of the image
3. pass the selected region to P-Net which returns a bounding box if it detects a face and a confidence there’s a face on the fragment
4. to reduce the number of bounding boxes use a confidence threshold and non-maximum suppression (once for every scaled image, then one more time with the surviving kernels from each scale)
5. pad bounding boxes regions if needed, resize them to 24*24, normalize to values in [-1;1] and pass them to R-Net
6. repeat steps (4-5), except now you need to resize to 48*48 shape and feed the result into O-Net
7. O-Net returns coordinates of a bounding box, 5 landmarks and a confidence level
8. repeat step (4)

We used [a pretrained MTCNN model](https://towardsdatascience.com/how-does-a-face-detection-program-work-using-neural-networks-17896df8e6ff) and it performed better and faster than the model from dlib. It was chosen as a final face detection model for the Windows version of the project.

### ML Kit face detector

[ML Kit](https://firebase.google.com/docs/ml-kit/?utm_source=studio) is a mobile SDK that brings Google’s machine learning expertise to Android. Of course, it contains a face detector too. The detection model could be either automatically downloaded during the app’s installation or the first time you run the detection on a device (we chose the 1st option).

The model’s input’s expected to be an image containing faces each at least 100*100 pixels. For faster performance on a device we set the next parameters of the model: FAST (in contrast to ACCURATE), NO_LANDMARKS, NO_CONTOURS and NO_CLASSIFICATIONS.

It’s designed to work on a mobile device with very limited computing power and that’s the detector’s model we use in the Android version of the project.

## Face alignment

An alignment function was implemented similar to the one from [this article](https://www.pyimagesearch.com/2017/05/22/face-alignment-with-opencv-and-python/). The function scales the distance between eyes, moves the center of the eyes to a fixed point and rotates the image so the eye line becomes horizontal.

However the neural network used for face description (described in the next section) is trained in such way that face alignment isn’t necessary (it slightly improves the performance but takes significant time costs). That’s why the face alignment function isn’t used in the final version of the Windows program.

## Face description

For face description we used 2 versions of pretrained FaceNet model: [an implemented using Pytorch](https://github.com/timesler/facenet-pytorch) for Windows and [a Keras model](https://github.com/nyoki-mtl/keras-facenet) converted to TFLite for Android.
The input for both versions is expected to be an image containing face (cropped using a relevant bounding box without extra alignments), resized to 160*160 and normalized to values in [-1;1].
The first version returns a 512-dimensional embedding. The output of the second one is 128-dimensional.

![Fig. 3. FaceNet structure: a batch input layer and a deep CNN followed by L2 normalization, which results in the face embedding. This is followed by the triplet loss during training](https://miro.medium.com/max/1936/1*ZD-mw2aUQfFwCLS3cV2rGA.png "Fig. 3. FaceNet structure: a batch input layer and a deep CNN followed by L2 normalization, which results in the face embedding. This is followed by the triplet loss during training")

*Fig. 3. FaceNet structure: a batch input layer and a deep CNN followed by L2 normalization, which results in the face embedding. This is followed by the triplet loss during training*

As [the official FaceNet paper](https://arxiv.org/pdf/1503.03832.pdf) says, the network is taught to map from face images to a compact Euclidean space. This means that to measure faces similarity a simple distance between vectors could be used. We used the threshold value 1.242 recommended in the paper for the squared L2-distance in the Windows version. However the converted Keras model used for Android was taught using cosine similarity and no threshold recommendations were attached. So to deal with unknown identities we experimentally deduced a threshold equal to 100 for the squared L2-distance.

## Face recognition

So now that we can describe faces using embedding vectors, we need to find a way to differentiate them.

The logic of face recognition is as follows:

- to add a new identity:
  - make 5 shots (simply 5 pictures of the same person)
  - for each shot detect and describe the face on it
  - calculate the average of the embeddings
  - assign the name to the new identity and save it with the resulting vector
- to recognize a person (for each face on a frame):
  - detect and describe the face
  - calculate the squared L2-distance between the vector and embeddings of all the saved identities
  - find the minimum of the distances
  - if it’s lower than the threshold, that’s an unknown person. Else the person is recognized as the identity with the minimum distance value

## The Windows version algorithm

Let’s summarize the algorithm of the Windows version of the project:

1. Use MTCNN for face detection
2. Get a 512-dimensional embedding of each face using FaceNet
3. Save it or compare to the ones you saved before to add or recognize the identity respectively

## Extra prerequisites for Android

There are some extra details about the android version worth a mention.

### CameraX

We used the most modern camera API provided by Google – CameraX (for more details follow [1](https://developer.android.com/training/camerax/architecture
), [2](https://www.youtube.com/watch?v=kuv8uK-5CLY), [3](https://gabrieltanner.org/blog/android-camerax), [4](https://codelabs.developers.google.com/codelabs/camerax-getting-started/#0) and [5](https://magdamiu.com/2020/09/16/smile-its-camerax-analysis-and-extensions/#:~:text=CameraX%20produces%20images%20in%20YUV_420_888,pixel%20%3D%20Y%20(grayscale%20image))). The API is compatible with [90% of devices](https://www.youtube.com/watch?v=kuv8uK-5CLY).

Of course, we need to use camera in our project, so the app asks for permission to use it.

Image preview and analysis are implemented and execute in separate threads, so a user could enjoy a continuous frames stream not affected by the analysis time costs.

The analyze() function detects and describes faces as described above. After the image analysis is over, it passes the data about faces locations and their embeddings.

The problem is the coordinates of the bounding boxes around faces are respective to the size of a frame in the image analysis. And the bounding boxes are drawn above the preview frame. However the sizes of frames in preview and analysis could differ.

![Fig. 4. Bounding boxes coordinates conversion. 1st column - the visible window, 2nd - a preview, 3rd - a frame in image analysis](https://github.com/MBichurin/face-recognition/raw/master/bboxes_coords_conversion.png?raw=true "Fig. 4. Bounding boxes coordinates conversion. 1st column - the visible window, 2nd - a preview, 3rd - a frame in image analysis")

*Fig. 4. Bounding boxes coordinates conversion. 1st column - the visible window, 2nd - a preview, 3rd - a frame in image analysis*

That’s why a conversion of coordinates is required (Fig. 4). The preview frame is cropped and scaled to fit the window (the active part of the screen). So we scale the window to the actual preview size and call the result as scaled window’s size. Then bounding boxes coordinates are converted from the analysis frame’s system to fit the scaled window’s size. These are the final coordinates used for bounding boxes’ display. But to display a name of a person we scale coordinates of the top left corner to the actual window’s size.

Also, if a frontal camera is used, we reflect a frame and bounding boxes’ coordinates so it imitates a mirror.

### Firebase

Firebase is a toolset for an easier use of tools provided by Google in your project. In our case we use ML Kit for face detection (though it grew up into a separate product and can be used without Firebase).

To use ML Kit we [connected the project to Firebase](https://console.firebase.google.com/) and installed config file from the created project’s page.

### Frames loss

Since it takes longer to analyze a frame than a desired FPS requires, not every frame is passed to the analyzer. So to show relevant information about faces, some frames don’t get into the analyzer’s queue of inputs.

### Saved identities

To implement recognition there’s a Map object with a name as a key and an embedding as a value in the program. When the app’s ran, it reads the current state of the Map from the file if it exists. After each operation of adding a new identity the program updates the file. Of course, to do so the application firstly asks for permission to work with the device’s storage.

## The Android version algorithm

Finally let’s look at the Android application’s algorithm:

1. Display a preview on the screen using CameraX
2. Run a separate thread for detection and description of faces, manage frames loss
3. Use ML Kit to detect faces on a frame in the CameraX analyzer
4. For each detected face pass its regions to FaceNet to get a 128-dimensional embedding
5. Save it or compare to the ones you saved before to add or recognize the identity respectively
6. In the UI thread draw bounding boxes and their labels
