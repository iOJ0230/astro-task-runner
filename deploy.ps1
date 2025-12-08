# deploy.ps1
# Simple one-button deployment for astro-task-runner
# Run from project root:  ./deploy.ps1

# ----- Configuration -----
$PROJECT_ID = "astro-task-runner"
$REGION     = "asia-southeast1"
$SERVICE    = "astro-task-runner"

Write-Host "👑 Astro Task Runner deploy script starting..." -ForegroundColor Cyan
Write-Host "Project: $PROJECT_ID, Region: $REGION, Service: $SERVICE" -ForegroundColor Cyan
Write-Host ""

# ----- Step 1: Run tests -----
Write-Host "🧪 Running tests (./gradlew clean test)..." -ForegroundColor Yellow
./gradlew clean test
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Tests failed. Aborting deploy." -ForegroundColor Red
    exit 1
}
Write-Host "✅ Tests passed." -ForegroundColor Green
Write-Host ""

# ----- Step 2: Build image with Cloud Build -----
$IMAGE = "gcr.io/$PROJECT_ID/$SERVICE"
Write-Host "🏗 Building image with Cloud Build: $IMAGE" -ForegroundColor Yellow
gcloud builds submit --tag $IMAGE
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Cloud Build failed. Aborting deploy." -ForegroundColor Red
    exit 1
}
Write-Host "✅ Image built and pushed: $IMAGE" -ForegroundColor Green
Write-Host ""

# ----- Step 3: Deploy to Cloud Run -----
Write-Host "☁️ Deploying to Cloud Run service '$SERVICE' in region '$REGION'..." -ForegroundColor Yellow
gcloud run deploy $SERVICE --image $IMAGE --platform=managed --region=$REGION --allow-unauthenticated
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Cloud Run deploy failed." -ForegroundColor Red
    exit 1
}
Write-Host "✅ Cloud Run deploy completed." -ForegroundColor Green
Write-Host ""

Write-Host "🌙 Deployment finished." -ForegroundColor Cyan
Write-Host "Open the Cloud Run console to copy the Service URL, then test:" -ForegroundColor Cyan
Write-Host '  curl "<RUN_URL>/health"' -ForegroundColor Cyan
Write-Host ""