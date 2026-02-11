resource "aws_iam_policy" "process_video_ssm" {
  name = "process-video-ssm-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:${local.aws_account_id}:parameter/process-video/*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::video-input-storage",
          "arn:aws:s3:::video-input-storage/*",
          "arn:aws:s3:::video-processed-storage",
          "arn:aws:s3:::video-processed-storage/*"
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_attach" {
  role       = aws_iam_role.process_video_irsa.name
  policy_arn = aws_iam_policy.process_video_ssm.arn
}
