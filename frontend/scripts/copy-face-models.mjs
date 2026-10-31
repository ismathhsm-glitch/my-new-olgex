import { mkdir, copyFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const source = resolve(root, 'node_modules/@vladmandic/face-api/model')
const destination = resolve(root, 'public/models')
const runtimeDestination = resolve(root, 'public/vendor')
const files = [
  'tiny_face_detector_model-weights_manifest.json',
  'tiny_face_detector_model.bin',
  'face_landmark_68_model-weights_manifest.json',
  'face_landmark_68_model.bin',
  'face_recognition_model-weights_manifest.json',
  'face_recognition_model.bin',
]

await mkdir(destination, { recursive: true })
await mkdir(runtimeDestination, { recursive: true })
for (const file of files) {
  await copyFile(resolve(source, file), resolve(destination, file))
}
await copyFile(resolve(root, 'node_modules/@vladmandic/face-api/dist/face-api.js'), resolve(runtimeDestination, 'face-api.js'))
console.log(`Copied ${files.length} face model files and the browser runtime.`)
