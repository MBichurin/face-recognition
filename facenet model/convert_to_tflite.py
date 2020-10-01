import tensorflow as tf

if __name__ == "__main__":
    keras_model = tf.keras.models.load_model('facenet_keras.h5')
    converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
    buffer = converter.convert()
    open('facenet.tflite', 'wb').write(buffer)