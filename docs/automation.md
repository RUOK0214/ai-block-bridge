# AI Block Bridge 2

기본 모드 0.2.0 이상과 애드온을 함께 설치합니다. Minecraft 26.2, Java 25,
Fabric Loader 0.19.5 이상 및 해당 Minecraft용 Fabric API가 필요합니다.
모드는 네트워크 연결을 열지 않습니다. AI 연동은 외부 프로그램의 파일 입출력으로 구현합니다.

1. 테스트 월드에서 OP 4 권한으로 /abb2 watch on 실행.
2. 표시된 월드 이름을 확인하고 30초 내 /abb2 watch confirm 실행.
3. 게임/서버 폴더의 ai-block-bridge-2/in에 요청을 임시 파일로 쓴 뒤 .json으로 이름 변경.
4. out/<id>.json 응답을 확인. 다음 요청에는 고유 id와 파일 이름을 사용.
5. /abb2 watch off로 취소. 이미 수행한 월드 변경은 되돌리지 않음.

요청 예시:

    {"id":"attempt-1","dimension":"minecraft:overworld","region":[0,64,0,7,66,0],"clear":true,"tickRate":200,"script":"0 0 0 | minecraft:stone","test":"@case stone\nexpect 0 0 0 | minecraft:stone"}

clear 기본값은 true입니다. 기존 회로를 테스트만 하려면 반드시 clear:false를 지정하세요.
tickRate는 서버 전체 속도이며 종료·취소 시 복원합니다. sprint는 선택적인 가속 틱 수입니다.

처리 중 파일은 processing에 있으며 응답 저장 후 done으로 이동합니다.
충돌·저장 실패 시 대기하거나 감시가 비활성화될 수 있습니다. 로그와 out을 확인하세요.
서버 중단 뒤 processing에 남은 요청은 자동 재실행하지 않습니다.
월드 상태를 확인하고 재실행이 필요한 경우에만 새 id/파일 이름으로 제출하세요.
done은 성공 여부가 아닌 응답 저장 완료를 의미합니다. 응답의 status와 test 리포트를 확인하세요.
