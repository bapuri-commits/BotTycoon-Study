## 개념 정리 노트 (tycoon-lite 스터디용)

- **프로젝트**: tycoon-lite (Minecraft Paper 플러그인)
- **용도**: 스터디 중 이 대화에서 새로 알게 된 개념만 정리하는 노트
- **작성 규칙**
  - 한 개념당 한 섹션
  - 가능하면 **“이 플러그인 코드에서 어디서/왜 쓰였는지”** 같이 적기
  - 아직 모르는 부분은 `TODO:` 로 남기기

---

## 목차

1. [Gradle vs Maven](#gradle-vs-maven)

---

## Gradle vs Maven

- **언제 나왔는지**  
  - 빌드/의존성 관리 도구 얘기가 나왔을 때, `mvn clean package` 를 쓰고 있어서 자연스럽게 궁금해짐.

- **한 줄 정의**  
  - **Maven**: XML(`pom.xml`) 기반의 전통적인 자바 빌드/의존성 관리 도구  
  - **Gradle**: Groovy/Kotlin DSL(`build.gradle(.kts)`)을 쓰는 **스크립트형 빌드 도구**로, 더 유연하고 대체로 더 빠름

- **조금 긴 설명**

  - **공통점**
    - 둘 다 **빌드 도구 + 의존성(라이브러리) 관리 도구**이다.
    - `pom.xml` 또는 `build.gradle` 안에 “이 프로젝트는 어떤 언어/버전, 어떤 라이브러리를 쓰고, 어떻게 패키징/테스트/배포할지”를 정의한다.
    - `mvn package`, `gradle build` 같은 단일 명령으로 **컴파일 → 테스트 → 패키징(JAR/ZIP)** 까지 자동 실행할 수 있다.

  - **Maven 특징**
    - 설정 파일: `pom.xml` (XML 포맷, **선언형**에 가깝다)
    - 장점:
      - 구조가 정형화되어 있고, “표준 Maven 프로젝트 구조”만 알면 대부분의 자바 프로젝트를 빠르게 이해 가능.
      - 예제/문서/튜토리얼이 매우 많고, 기업 코드베이스에서도 여전히 널리 사용된다.
    - 단점:
      - XML 문법이 장황해서, 복잡한 빌드 로직을 표현하려면 설정이 길고 복잡해지기 쉽다.
      - “규칙에서 많이 벗어나는” 특이한 빌드를 하고 싶을 때 유연성이 떨어질 수 있다.

  - **Gradle 특징**
    - 설정 파일: `build.gradle` (Groovy DSL) 또는 `build.gradle.kts` (Kotlin DSL)
    - 장점:
      - 빌드 스크립트 자체가 **프로그램 코드**라서, 조건문/함수/루프 등을 자유롭게 쓸 수 있어 빌드 로직을 매우 유연하게 작성 가능.
      - 기본적으로 **증분 빌드/캐시**를 잘 활용해서, 대형 프로젝트에서 빌드 속도가 빠른 편이다.
      - 안드로이드 공식 빌드 도구로 채택되어 있어서, 모바일 쪽에서는 사실상 표준.
    - 단점:
      - 처음 보면 DSL 문법이 다소 낯설 수 있고, 자유도가 높은 만큼 프로젝트마다 스타일이 많이 다를 수 있다.

- **이 플러그인(tycoon-lite) 맥락에서**

  - 현재 이 프로젝트는 **Maven** 기반 (`pom.xml`, `mvn clean package`) 으로 보인다.
  - **플러그인 개발자의 입장**에서 중요한 점:
    - “빌드를 어떻게 하느냐(Gradle vs Maven)”보다,  
      **“빌드 도구를 통해 의존성을 어떻게 추가/관리하고, 빌드 산출물(JAR)을 어떻게 서버에 배포하느냐”**가 더 핵심이다.
    - tycoon-lite 코드를 이해하고 기능을 추가하는 데는  
      “Maven/Gradle 구문 차이”보다 **`dependencies` 에 어떤 라이브러리가 들어가 있고, 그게 코드에서 어떻게 쓰이는지** 를 읽을 줄 아는 게 더 중요하다.

  - 나중에 **비슷한 수준의 새 플러그인**을 만들 때:
    - 이미 Maven 템플릿을 잘 이해했다면 → **Maven 그대로 쓰는 것**이 가장 실용적이고 빠르다.
    - 다른 예제/튜토리얼이 Gradle 기반이라서 따라 하고 싶다면 → **Gradle 템플릿으로 새 프로젝트를 만들고, 구조/코드만 이식**하는 것도 가능하다.
    - 둘은 “철학과 설정 문법”이 다를 뿐, **“플러그인 JAR를 만든다”는 최종 목표는 같다.**

- **간단 예시 (개념 비교용)**

  - Maven에서 의존성 추가 예시:

  ```xml
  <!-- pom.xml -->
  <dependencies>
      <dependency>
          <groupId>org.spigotmc</groupId>
          <artifactId>spigot-api</artifactId>
          <version>1.20.1-R0.1-SNAPSHOT</version>
          <scope>provided</scope>
      </dependency>
  </dependencies>
  ```

  - Gradle에서 의존성 추가 예시 (Groovy DSL):

  ```groovy
  // build.gradle
  dependencies {
      compileOnly "org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT"
  }
  ```

- **관련 개념 / 같이 보면 좋은 것**
  - 빌드 생명주기(lifecycle): `compile`, `test`, `package`, `install` 등
  - 의존성 범위(scope) / configuration: `compile`, `provided`, `runtimeOnly` 등
  - 멀티 모듈 프로젝트 구조 (서버 플러그인, 클라이언트 모드 등 여러 모듈을 한 번에 관리할 때)

- **아직 헷갈리는 점 / 질문 (메모용)**
  - TODO: 나중에 이 프로젝트에서 실제로 `pom.xml` 구조를 뜯어보면서,  
    “Paper/Spigot API 의존성”, “Vault/Lands 같은 외부 플러그인 의존성” 이 각각 어떻게 선언되어 있는지 정리해 보기.

