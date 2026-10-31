const MODEL_PATH = '/models'
let modelsLoaded: Promise<void> | null = null
type FaceApiModule = typeof import('@vladmandic/face-api')
let faceApiModule: FaceApiModule | null = null
let faceApiLoad: Promise<FaceApiModule> | null = null

declare global {
  interface Window {
    faceapi?: FaceApiModule
  }
}

async function getFaceApi(): Promise<FaceApiModule> {
  if (faceApiModule) return faceApiModule
  if (window.faceapi) {
    faceApiModule = window.faceapi
    return faceApiModule
  }
  if (!faceApiLoad) {
    faceApiLoad = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = '/vendor/face-api.js'
      script.async = true
      script.onload = () => {
        if (!window.faceapi) {
          faceApiLoad = null
          reject(new Error('FACE_API_RUNTIME_UNAVAILABLE'))
          return
        }
        faceApiModule = window.faceapi
        resolve(faceApiModule)
      }
      script.onerror = () => {
        faceApiLoad = null
        script.remove()
        reject(new Error('FACE_API_RUNTIME_UNAVAILABLE'))
      }
      document.head.append(script)
    })
  }
  faceApiModule = await faceApiLoad
  return faceApiModule
}

export async function loadFaceModels() {
  if (!modelsLoaded) {
    modelsLoaded = getFaceApi().then((faceapi) => Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_PATH),
      faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_PATH),
      faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_PATH),
    ])).then(() => undefined).catch((error: unknown) => {
      modelsLoaded = null
      throw new Error('FACE_MODELS_UNAVAILABLE', { cause: error })
    })
  }
  return modelsLoaded
}

export async function captureDescriptor(video: HTMLVideoElement): Promise<number[]> {
  await loadFaceModels()
  const faceapi = await getFaceApi()
  const detections = await faceapi.detectAllFaces(video, new faceapi.TinyFaceDetectorOptions())
    .withFaceLandmarks()
    .withFaceDescriptors()
  if (detections.length === 0) throw new Error('NO_FACE')
  if (detections.length > 1) throw new Error('MULTIPLE_FACES')
  const descriptor = Array.from(detections[0].descriptor)
  if (descriptor.length !== 128 || descriptor.some((value) => !Number.isFinite(value))) throw new Error('INVALID_DESCRIPTOR')
  return descriptor
}
