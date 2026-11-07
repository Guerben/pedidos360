# Script PowerShell para unir rabbit2 al cluster de rabbit1 (EP4)
Write-Host "Uniendo rabbit2 al cluster con rabbit1..." -ForegroundColor Cyan

docker exec rabbit2 rabbitmqctl stop_app
docker exec rabbit2 rabbitmqctl reset
docker exec rabbit2 rabbitmqctl join_cluster rabbit@rabbit1
docker exec rabbit2 rabbitmqctl start_app

Write-Host "`nEstado del cluster:" -ForegroundColor Green
docker exec rabbit1 rabbitmqctl cluster_status
