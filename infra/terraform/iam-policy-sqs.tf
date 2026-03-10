resource "aws_iam_policy" "process_video_sqs" {
  name = "process-video-sqs-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:SendMessage"
      ]
      Resource = [
        data.terraform_remote_state.sqs.outputs.sqs_queue_arns["video-process-command"],
        data.terraform_remote_state.sqs.outputs.sqs_queue_arns["video-updated-event"]
      ]
    }]
  })
}

resource "aws_iam_role_policy_attachment" "sqs_attach" {
  role       = aws_iam_role.process_video_irsa.name
  policy_arn = aws_iam_policy.process_video_sqs.arn
}
