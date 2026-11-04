// Validation lives in one shared module that mirrors content/schema + tools/content/validate.mjs.
export {
  validateProcedure,
  validateFault,
  validateQuiz,
  validateMission,
  validateMissions,
} from "@/lib/data/validate";
