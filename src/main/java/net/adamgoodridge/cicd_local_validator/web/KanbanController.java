package net.adamgoodridge.cicd_local_validator.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class KanbanController {
	@GetMapping("/kanban")
	public String kanban(Model model) {
		model.addAttribute("columns", List.of("PENDING", "RUNNING", "PASSED", "FAILED", "BLOCKED", "SKIPPED"));
		return "kanban";
	}
}
