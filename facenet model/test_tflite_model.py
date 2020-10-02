import tensorflow as tf
import numpy as np

if __name__ == '__main__':
    # Load the model and allocate tensors
    interpreter = tf.lite.Interpreter(model_path="tflite_facenet_from_program.tflite")
    interpreter.allocate_tensors()

    # Get input and output tensors' details
    input_details, output_details =\
        interpreter.get_input_details(), interpreter.get_output_details()

    # Generate data and pass it as an input
    input_shape = input_details[0]['shape']
    print(input_shape)
    input = np.array(np.random.random_sample(input_shape), dtype=np.float32)
    interpreter.set_tensor(input_details[0]['index'], input)

    # Get output
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    print(output)
    print(output.shape)