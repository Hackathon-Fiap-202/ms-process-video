resource "aws_ecr_repository" "process_video" {
  name                 = "process-video"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}
